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
import java.util.Map;

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
            @RequestParam(required = false) String serviceName,
            @RequestParam(required = false) String api,
            @RequestParam(required = false) String method,
            @RequestParam(defaultValue = "24h") String range) {

        TimeRange timeRange = TimeRange.of(range);
        int windowMinutes = TrendQuery.windowMinutes(timeRange);
        StatsQuery query = filters(appId, serviceName, api, method);
        query.setWindowMinutes(windowMinutes);

        StatsAgg agg = overviewMapper.statsAgg(query);
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
        summary.put("p95", percentile(query, total, 0.95));
        summary.put("p99", percentile(query, total, 0.99));
        // QPS 按所选区间折算，1 分钟窗口下即为该分钟的每秒请求数
        summary.put("qps", round2(total / Math.max(1, windowMinutes * 60d)));
        summary.put("inbound", humanBytes(reqBytes));
        summary.put("outbound", humanBytes(resBytes));
        summary.put("inboundBytes", reqBytes);
        summary.put("outboundBytes", resBytes);
        // 规模指标：全平台口径，不受上面的过滤条件影响
        summary.put("appCount", overviewMapper.statsAppCount());
        summary.put("serviceCount", overviewMapper.countServices());
        summary.put("apiCount", overviewMapper.statsApiCount());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("range", Map.of("code", timeRange.getCode(),
                "label", timeRange.getRangeLabel(),
                "minutes", windowMinutes));
        data.put("summary", summary);
        data.put("statusDist", overviewMapper.statusDist(query));
        data.put("methodDist", overviewMapper.methodDist(query));
        data.put("topServices", overviewMapper.topServices(query));
        data.put("topApis", overviewMapper.topApis(query));
        return R.ok(data);
    }

    /**
     * 请求量趋势：时间区间 / 桶对齐与网关总览的 QPS 趋势完全一致，
     * 区别只在于这里返回桶内请求量（总览返回 QPS），并且同样支持四个维度过滤。
     *
     * @param range 统一时间区间（同 summary），固定切 60 个点
     */
    @GetMapping("/trend")
    public R<Map<String, Object>> trend(
            @RequestParam(required = false) String appId,
            @RequestParam(required = false) String serviceName,
            @RequestParam(required = false) String api,
            @RequestParam(required = false) String method,
            @RequestParam(defaultValue = "24h") String range) {
        TimeRange timeRange = TimeRange.of(range);
        StatsQuery query = filters(appId, serviceName, api, method);
        StatsQuery trend = TrendQuery.query(timeRange);
        query.setBucketSec(trend.getBucketSec());
        query.setWindowMinutes(trend.getWindowMinutes());
        return R.ok(TrendQuery.build(overviewMapper.trendBuckets(query), timeRange, false));
    }

    // ------------------------------------------------------------------ 内部工具

    /** 维度过滤条件：summary 与 trend 共用，保证两处口径不变 */
    private StatsQuery filters(String appId, String serviceName, String api, String method) {
        StatsQuery query = new StatsQuery();
        if (StringUtils.hasText(appId)) {
            query.setAppId(appId);
        }
        if (StringUtils.hasText(serviceName)) {
            query.setServiceName(serviceName);
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

    /** 分位耗时（P95 / P99）：MySQL 5.7 无窗口函数，用「排序 + 偏移量」取分位点 */
    private int percentile(StatsQuery query, long total, double quantile) {
        if (total <= 0) {
            return 0;
        }
        query.setOffset(Math.min((int) Math.floor(total * quantile), (int) total - 1));
        Long value = overviewMapper.statsPercentile(query);
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
