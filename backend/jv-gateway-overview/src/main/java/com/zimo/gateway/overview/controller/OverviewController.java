package com.zimo.gateway.overview.controller;

import com.zimo.gateway.common.result.R;
import com.zimo.gateway.common.util.TimeRange;
import com.zimo.gateway.overview.mapper.OverviewMapper;
import com.zimo.gateway.overview.model.StatsAgg;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.lang.management.GarbageCollectorMXBean;
import java.time.Duration;
import java.time.LocalDateTime;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.ThreadMXBean;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 网关总览：全局指标。
 *
 * <p>所有数字都来自 tb_gw_trace_log / tb_gw_service 的实时聚合与本机 JVM，
 * SQL 全部由 {@link OverviewMapper}（原生 MyBatis）维护。
 */
@Slf4j
@RestController
@RequestMapping("/overview")
@RequiredArgsConstructor
public class OverviewController {

    private final OverviewMapper overviewMapper;

    /**
     * 汇总指标：应用 / 服务 / 接口个数、区间 QPS、请求数、延迟、错误率、流量。
     *
     * @param range 统一时间区间：1m / 5m / 10m / 30m / 1h / 2h / 24h / 1d / 7d / 30d，默认 24h
     */
    @GetMapping("/summary")
    public R<Map<String, Object>> summary(@RequestParam(defaultValue = "24h") String range) {
        TimeRange timeRange = TimeRange.of(range);
        long windowSeconds = Math.max(1, Duration.between(timeRange.since(), LocalDateTime.now()).getSeconds());

        long appCount = overviewMapper.countApps();
        long serviceCount = overviewMapper.countServices();
        long onlineCount = overviewMapper.countOnlineServices();
        long apiCount = overviewMapper.countApis();

        // 区间请求数折算到每秒，即为该区间的平均 QPS
        long requests = overviewMapper.rangeCount(windowSeconds);
        BigDecimal qps = BigDecimal.valueOf(requests)
                .divide(BigDecimal.valueOf(windowSeconds), 2, RoundingMode.HALF_UP);

        StatsAgg agg = overviewMapper.rangeStats(windowSeconds);
        long total = number(agg.getTotal()).longValue();
        long errors = number(agg.getErrors()).longValue();
        double avgRt = number(agg.getAvgRt()).doubleValue();
        long reqBytes = number(agg.getReqBytes()).longValue();
        long resBytes = number(agg.getResBytes()).longValue();
        double errorRate = total == 0 ? 0d : errors * 100d / total;

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("range", Map.of("code", timeRange.getCode(), "label", timeRange.getRangeLabel(),
                "seconds", windowSeconds));
        data.put("appCount", appCount);
        data.put("serviceCount", serviceCount);
        data.put("onlineCount", onlineCount);
        data.put("apiCount", apiCount);
        data.put("qps", qps.doubleValue());
        data.put("requests", total);
        // 兼容原字段：前端已统一按「区间请求数」展示
        data.put("todayRequests", total);
        data.put("avgRt", Math.round(avgRt));
        data.put("p95", percentile(windowSeconds, total, 0.95));
        data.put("p99", percentile(windowSeconds, total, 0.99));
        data.put("errorRate", round2(errorRate));
        data.put("healthScore", healthScore(serviceCount, onlineCount, errorRate));
        data.put("inbound", humanBytes(reqBytes));
        data.put("outbound", humanBytes(resBytes));
        return R.ok(data);
    }

    /**
     * QPS 趋势：按统一时间区间（{@link TimeRange}）取窗口，区间内固定切 60 个点。
     *
     * <p>区间越短桶越细（1 分钟区间 1 秒/点），区间越长桶越粗（30 天 12 小时/点），
     * 与数据统计页共用 TrendQuery / OverviewMapper.trendBuckets。
     *
     * @param range 1m / 5m / 10m / 30m / 1h / 2h / 24h / 1d / 7d / 30d，默认 24h
     */
    @GetMapping("/trend")
    public R<Map<String, Object>> trend(@RequestParam(defaultValue = "24h") String range) {
        TimeRange timeRange = TimeRange.of(range);
        return R.ok(TrendQuery.build(
                overviewMapper.trendBuckets(TrendQuery.query(timeRange)), timeRange, true));
    }

    /**
     * 运行时指标：本机 JVM 实时数据 + 今日调用质量。
     */
    @GetMapping("/runtime")
    public R<List<Map<String, Object>>> runtime() {
        List<Map<String, Object>> list = new ArrayList<>(6);

        MemoryUsage heap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
        long usedMb = heap.getUsed() / 1024 / 1024;
        long maxMb = heap.getMax() > 0 ? heap.getMax() / 1024 / 1024 : 0;
        String heapPct = maxMb == 0 ? "-" : String.format("%.1f%%", usedMb * 100d / maxMb);
        list.add(metric("JVM 堆内存", usedMb + " MB / " + maxMb + " MB", heapPct, "当前进程实时值"));

        long gcCount = 0;
        long gcTime = 0;
        for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
            if (gc.getCollectionCount() > 0) {
                gcCount += gc.getCollectionCount();
                gcTime += gc.getCollectionTime();
            }
        }
        list.add(metric("GC 次数 / 耗时", gcCount + " 次 / " + gcTime + " ms", "累计", "自进程启动"));

        ThreadMXBean thread = ManagementFactory.getThreadMXBean();
        list.add(metric("活跃线程数", thread.getThreadCount() + " / 峰值 " + thread.getPeakThreadCount(),
                "实时", "JVM 线程"));

        OperatingSystemMXBean os = ManagementFactory.getOperatingSystemMXBean();
        double load = os.getSystemLoadAverage();
        list.add(metric("系统负载", load < 0 ? "-" : String.format("%.2f", load),
                os.getAvailableProcessors() + " 核", "1 分钟平均"));

        StatsAgg today = overviewMapper.todayStats();
        long total = number(today.getTotal()).longValue();
        double errorRate = total == 0 ? 0d : number(today.getErrors()).doubleValue() * 100 / total;
        list.add(metric("今日平均耗时", number(today.getAvgRt()).longValue() + " ms",
                "样本 " + total, "来自调用日志"));
        list.add(metric("今日错误率", round2(errorRate) + "%",
                total == 0 ? "无调用" : "错误 " + number(today.getErrors()).longValue(),
                "status_code >= 400"));

        return R.ok(list);
    }

    /**
     * 服务实例：真实注册在平台上的服务，调用量与耗时按所选区间统计。
     *
     * @param range 统一时间区间（同 summary）
     */
    @GetMapping("/instances")
    public R<List<Map<String, Object>>> instances(@RequestParam(defaultValue = "24h") String range) {
        long windowSeconds = Math.max(1,
                Duration.between(TimeRange.of(range).since(), LocalDateTime.now()).getSeconds());
        List<Map<String, Object>> rows = overviewMapper.instances(windowSeconds);
        List<Map<String, Object>> list = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", row.get("service_name"));
            item.put("appName", row.get("app_name"));
            item.put("ip", row.get("ip") == null ? "-" : row.get("ip"));
            item.put("port", row.get("port"));
            // QPS 取近 60 秒计数，平均耗时取近 5 分钟，均为真实调用记录
            item.put("qps", number(row.get("qps")).intValue());
            // 近 5 分钟调用次数：实例列表的排序依据（调用排名靠前的服务排前面）
            item.put("calls", number(row.get("calls")).intValue());
            item.put("avgRt", Math.round(number(row.get("avg_cost")).doubleValue()));
            item.put("lastHeartbeat", row.get("last_heartbeat_time"));
            item.put("status", row.get("status"));
            list.add(item);
        }
        return R.ok(list);
    }

    // ------------------------------------------------------------------ 内部方法

    /** 区间 P95 / P99：MySQL 5.7 没有窗口函数，用「排序 + 偏移量」取分位点 */
    private int percentile(long windowSeconds, long total, double quantile) {
        if (total <= 0) {
            return 0;
        }
        int offset = Math.min((int) Math.floor(total * quantile), (int) total - 1);
        Long value = overviewMapper.rangePercentile(windowSeconds, offset);
        return value == null ? 0 : value.intValue();
    }

    /** 今日 P95 / P99：运行时指标区仍按自然日口径 */
    private int todayPercentile(long total, double quantile) {
        if (total <= 0) {
            return 0;
        }
        int offset = Math.min((int) Math.floor(total * quantile), (int) total - 1);
        Long value = overviewMapper.todayPercentile(offset);
        return value == null ? 0 : value.intValue();
    }

    /** 健康分：在线率占 60%，错误率占 40% */
    private double healthScore(long serviceCount, long onlineCount, double errorRate) {
        if (serviceCount == 0) {
            return 100d;
        }
        double onlineRate = onlineCount * 1.0 / serviceCount;
        double quality = Math.max(0d, 1 - errorRate / 100d);
        return round2((onlineRate * 0.6 + quality * 0.4) * 100);
    }

    private static Number number(Object value) {
        return value instanceof Number ? (Number) value : BigDecimal.ZERO;
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

    private static Map<String, Object> metric(String label, String value, String trend, String hint) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("label", label);
        map.put("value", value);
        map.put("trend", trend);
        map.put("hint", hint);
        return map;
    }
}
