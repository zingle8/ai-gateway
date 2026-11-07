package com.zimo.gateway.overview.controller;

import com.zimo.gateway.common.result.R;
import com.zimo.gateway.common.util.TimeRange;
import com.zimo.gateway.overview.mapper.OverviewMapper;
import com.zimo.gateway.overview.model.StatsAgg;
import com.zimo.gateway.overview.model.StatsQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 数据统计：在网关总览的指标口径上，支持按 应用 / 服务 / 接口 / 方法 维度过滤。
 *
 * <p>所有数字都来自 tb_gw_trace_log 的实时聚合，SQL 由 {@link OverviewMapper}（原生 MyBatis）维护，
 * 动态条件集中在 XML 的 statsFilters 片段，杜绝字符串拼 SQL。
 * 指标卡 / 分布 / Top 榜与趋势共用同一套时间区间（{@link TimeRange}，1分钟 ~ 30天 共 10 档），
 * 保证「所选区间」与页面上的每个数字口径一致。
 */
@RestController
@RequestMapping("/stats")
@RequiredArgsConstructor
public class StatsController {

    private final OverviewMapper overviewMapper;

    /**
     * 过滤条件字典：应用 / 服务 / 接口 / 方法，取库中真实出现过的值。
     */
    @GetMapping("/filters")
    public R<Map<String, Object>> filters() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("apps", overviewMapper.filterApps());
        data.put("services", overviewMapper.distinctServices());
        data.put("apis", overviewMapper.distinctApis());
        data.put("methods", overviewMapper.distinctMethods());
        return R.ok(data);
    }

    /**
     * 统计主体：指标卡、状态码/方法分布、Top 服务与接口。
     *
     * @param range 统一时间区间：1m / 5m / 10m / 30m / 1h / 2h / 24h / 1d / 7d / 30d，默认 24h
     */
    @GetMapping("/summary")
    public R<Map<String, Object>> summary(
            @RequestParam(required = false) String appId,
            @RequestParam(required = false) String serviceId,
            @RequestParam(required = false) String api,
            @RequestParam(required = false) String method,
            @RequestParam(defaultValue = "24h") String range) {

        TimeRange timeRange = TimeRange.of(range);
        int windowMinutes = TrendQuery.windowMinutes(timeRange);
        StatsQuery query = filters(appId, serviceId, api, method);
        query.setWindowMinutes(windowMinutes);

        // 一次页面 8 条独立查询，并行执行（串行时随区间变大线性劣化）
        var fAgg = CompletableFuture.supplyAsync(() -> overviewMapper.statsAgg(query));
        var fCosts = CompletableFuture.supplyAsync(() -> overviewMapper.statsCosts(query));
        var fStatus = CompletableFuture.supplyAsync(() -> overviewMapper.statusDist(query));
        var fMethod = CompletableFuture.supplyAsync(() -> overviewMapper.methodDist(query));
        var fTopSvc = CompletableFuture.supplyAsync(() -> overviewMapper.topServices(query));
        var fTopApi = CompletableFuture.supplyAsync(() -> overviewMapper.topApis(query));
        var fAppCount = CompletableFuture.supplyAsync(overviewMapper::statsAppCount);
        var fApiCount = CompletableFuture.supplyAsync(overviewMapper::statsApiCount);

        StatsAgg agg = fAgg.join();
        long total = num(agg.getTotal()).longValue();
        long errors = num(agg.getErrors()).longValue();
        long reqBytes = num(agg.getReqBytes()).longValue();
        long resBytes = num(agg.getResBytes()).longValue();

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalRequests", total);
        summary.put("errorCount", errors);
        summary.put("successCount", total - errors);
        summary.put("errorRate", percent(errors, total));
        summary.put("successRate", total == 0 ? 100d : round2((total - errors) * 100d / total));
        summary.put("avgRt", round2(num(agg.getAvgRt()).doubleValue()));
        List<Long> costs = fCosts.join();
        summary.put("p95", percentileFromCosts(costs, 0.95));
        summary.put("p99", percentileFromCosts(costs, 0.99));
        // QPS 按所选区间折算，1 分钟窗口下即为该分钟的每秒请求数
        summary.put("qps", round2(total / Math.max(1, windowMinutes * 60d)));
        summary.put("inbound", humanBytes(reqBytes));
        summary.put("outbound", humanBytes(resBytes));
        summary.put("inboundBytes", reqBytes);
        summary.put("outboundBytes", resBytes);
        // 规模指标：全平台口径，不受上面的过滤条件影响
        summary.put("appCount", fAppCount.join());
        summary.put("serviceCount", overviewMapper.countServices());
        summary.put("apiCount", fApiCount.join());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("range", Map.of("code", timeRange.getCode(),
                "label", timeRange.getRangeLabel(),
                "minutes", windowMinutes));
        data.put("summary", summary);
        data.put("statusDist", fStatus.join());
        data.put("methodDist", fMethod.join());
        data.put("topServices", fTopSvc.join());
        data.put("topApis", fTopApi.join());
        return R.ok(data);
    }

    /**
     * 趋势数据：时间区间 / 桶对齐与网关总览完全一致，同样支持四个维度过滤。
     *
     * <p>qps=true 时一次查询同时返回两套序列：{@code values} 为 QPS（桶内请求数 ÷ 桶宽），
     * {@code requestValues} 为桶内请求量——页面上的「请求量趋势」与「QPS 趋势」共用这次查询。
     *
     * @param range 统一时间区间（同 summary），固定切 60 个点
     * @param serviceId 服务ID（英文唯一标识）过滤
     */
    @GetMapping("/trend")
    public R<Map<String, Object>> trend(
            @RequestParam(required = false) String appId,
            @RequestParam(required = false) String serviceId,
            @RequestParam(required = false) String api,
            @RequestParam(required = false) String method,
            @RequestParam(defaultValue = "24h") String range) {
        TimeRange timeRange = TimeRange.of(range);
        StatsQuery query = filters(appId, serviceId, api, method);
        StatsQuery trend = TrendQuery.query(timeRange);
        query.setBucketSec(trend.getBucketSec());
        query.setWindowMinutes(trend.getWindowMinutes());
        return R.ok(TrendQuery.build(overviewMapper.trendBuckets(query), timeRange, true));
    }

    // ------------------------------------------------------------------ 内部工具

    /** 维度过滤条件：summary 与 trend 共用，保证两处口径不变 */
    private StatsQuery filters(String appId, String serviceId, String api, String method) {
        StatsQuery query = new StatsQuery();
        if (StringUtils.hasText(appId)) {
            query.setAppId(appId);
        }
        if (StringUtils.hasText(serviceId)) {
            query.setServiceId(serviceId);
        }
        if (StringUtils.hasText(api)) {
            query.setApi(api);
        }
        if (StringUtils.hasText(method)) {
            // api 形如 "GET /api/xxx"，方法过滤即匹配前缀
            query.setMethodLike(method.toUpperCase() + " %");
        }
        return query;
    }

    /** 分位耗时：一次覆盖索引扫描取回 cost 列，Java 排序算多分位（替代多次 OFFSET filesort） */
    private int percentileFromCosts(List<Long> costs, double quantile) {
        if (costs == null || costs.isEmpty()) {
            return 0;
        }
        List<Long> sorted = new ArrayList<>(costs);
        Collections.sort(sorted);
        int idx = (int) Math.min(Math.floor(sorted.size() * quantile), sorted.size() - 1);
        Long value = sorted.get(idx);
        return value == null ? 0 : value.intValue();
    }

    private static Number num(Object value) {
        return value instanceof Number ? (Number) value : BigDecimal.ZERO;
    }

    private static double percent(long part, long total) {
        return total == 0 ? 0d : round2(part * 100d / total);
    }

    private static double round2(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    private static String humanBytes(long bytes) {
        if (bytes <= 0) {
            return "0 B";
        }
        String[] units = {"B", "KB", "MB", "GB", "TB"};
        int idx = 0;
        double size = bytes;
        while (size >= 1024 && idx < units.length - 1) {
            size /= 1024;
            idx++;
        }
        return String.format("%.2f %s", size, units[idx]);
    }
}
