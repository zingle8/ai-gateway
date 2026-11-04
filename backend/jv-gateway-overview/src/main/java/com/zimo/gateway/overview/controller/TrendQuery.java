package com.zimo.gateway.overview.controller;

import com.zimo.gateway.overview.model.StatsQuery;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 趋势分桶公共实现：网关总览的 QPS 趋势 与 数据统计的请求量趋势 共用。
 *
 * <p>SQL 由 OverviewMapper.trendBuckets 执行，本类负责构造查询条件、固定档位、
 * 以及把稀疏的桶补齐成 120 个连续点位。
 * 桶对齐用 epoch 秒整除（分钟取模在跨小时 / 跨天时会错位）。
 */
final class TrendQuery {

    /** 合法粒度白名单（分钟/桶），非法值回落到 1 分钟 */
    static final Set<Integer> ALLOWED_STEPS = Set.of(1, 5, 10, 30, 60, 120, 1440);

    private static final int POINTS = 120;
    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter MM_DD = DateTimeFormatter.ofPattern("MM-dd");

    private TrendQuery() {
    }

    static int normalizeStep(int step) {
        return ALLOWED_STEPS.contains(step) ? step : 1;
    }

    static int windowMinutes(int stepMinutes) {
        return stepMinutes * POINTS;
    }

    static long bucketSec(int stepMinutes) {
        return stepMinutes * 60L;
    }

    /** 构造趋势查询条件；统计侧可在此基础上继续设置 appId / serviceName / api / methodLike */
    static StatsQuery query(int step) {
        int stepMinutes = normalizeStep(step);
        StatsQuery query = new StatsQuery();
        query.setBucketSec(bucketSec(stepMinutes));
        query.setWindowMinutes(windowMinutes(stepMinutes));
        return query;
    }

    /**
     * 把 mapper 返回的稀疏桶补成 120 个连续点。
     *
     * @param rows mapper 结果，每行含 b（桶起点 epoch 秒）、c（桶内请求数）
     * @param qps  true = 桶内请求数 ÷ 桶宽秒数（总览 QPS），false = 桶内请求量（数据统计）
     */
    static Map<String, Object> build(List<Map<String, Object>> rows, int step, boolean qps) {
        int stepMinutes = normalizeStep(step);
        long bucketSec = bucketSec(stepMinutes);

        Map<Long, Long> buckets = new LinkedHashMap<>();
        if (rows != null) {
            for (Map<String, Object> row : rows) {
                Object key = row.get("b");
                Object count = row.get("c");
                if (key instanceof Number && count instanceof Number) {
                    buckets.put(((Number) key).longValue(), ((Number) count).longValue());
                }
            }
        }

        long baseEpoch = System.currentTimeMillis() / 1000 / bucketSec * bucketSec;
        ZoneId zone = ZoneId.systemDefault();
        DateTimeFormatter labelFmt = stepMinutes >= 1440 ? MM_DD : HH_MM;
        List<String> times = new ArrayList<>(POINTS);
        List<Number> values = new ArrayList<>(POINTS);
        for (int i = POINTS - 1; i >= 0; i--) {
            long epoch = baseEpoch - (long) i * bucketSec;
            long count = buckets.getOrDefault(epoch, 0L);
            times.add(LocalDateTime.ofInstant(Instant.ofEpochSecond(epoch), zone).format(labelFmt));
            if (qps) {
                // 保留 1 位小数：取整会把 0.49 压成 0，低流量时段整条线会假性归零
                values.add(Math.round(count * 10.0 / bucketSec) / 10.0);
            } else {
                values.add(count);
            }
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("step", stepMinutes);
        data.put("times", times);
        data.put("values", values);
        return data;
    }
}
