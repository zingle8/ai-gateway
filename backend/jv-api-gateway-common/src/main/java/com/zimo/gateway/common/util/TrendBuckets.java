package com.zimo.gateway.common.util;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 趋势分桶公共实现：网关总览 / 数据统计 / 服务发现 的趋势图共用。
 *
 * <p>输入 mapper 返回的稀疏桶（每行 b = 桶起点 epoch 秒、c = 桶内请求数），
 * 补齐成 {@link TimeRange#POINTS} 个连续点位。桶对齐用 epoch 秒整除
 * （分钟取模在跨小时 / 跨天时会错位）。
 */
public final class TrendBuckets {

    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter HH_MM_SS = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter MM_DD_HH_MM = DateTimeFormatter.ofPattern("MM-dd HH:mm");
    private static final DateTimeFormatter MM_DD = DateTimeFormatter.ofPattern("MM-dd");

    private TrendBuckets() {
    }

    /**
     * 把稀疏桶补成连续点位。
     *
     * @param rows mapper 结果，每行含 b（桶起点 epoch 秒）、c（桶内请求数）
     * @param qps  true = 主序列为 QPS（桶内请求数 ÷ 桶宽秒数），并额外返回
     *             {@code requestValues}（桶内请求数序列，供「请求量趋势」图共用同一次查询）；
     *             false = 主序列为桶内请求量
     * @return {range, bucket, points, times, values[, requestValues]}
     */
    public static Map<String, Object> build(List<Map<String, Object>> rows, TimeRange range, boolean qps) {
        long bucketSec = range.getBucketSeconds();
        ZoneId zone = ZoneId.systemDefault();

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

        long nowEpoch = System.currentTimeMillis() / 1000;
        long baseEpoch = nowEpoch / bucketSec * bucketSec;
        int points = TimeRange.POINTS;
        if (range == TimeRange.D1) {
            // 1 天 = 自然日：从今日 00:00 起画，点数随当前时刻变化，
            // 与「24 小时」（滚动 24h）在图表上明确区分开
            long dayStart = LocalDate.now().atStartOfDay(zone).toEpochSecond();
            baseEpoch = dayStart + ((nowEpoch - dayStart) / bucketSec) * bucketSec;
            points = (int) Math.min(TimeRange.POINTS, (baseEpoch - dayStart) / bucketSec + 1);
        }

        DateTimeFormatter labelFmt = labelFormatter(range.getWindowSeconds(), bucketSec);
        List<String> times = new ArrayList<>(points);
        List<Number> values = new ArrayList<>(points);
        List<Number> requestValues = new ArrayList<>(points);
        for (int i = points - 1; i >= 0; i--) {
            long epoch = baseEpoch - (long) i * bucketSec;
            long count = buckets.getOrDefault(epoch, 0L);
            times.add(LocalDateTime.ofInstant(Instant.ofEpochSecond(epoch), zone).format(labelFmt));
            requestValues.add(count);
            if (qps) {
                // 保留 1 位小数：取整会把 0.49 压成 0，低流量时段整条线会假性归零
                values.add(Math.round(count * 10.0 / bucketSec) / 10.0);
            } else {
                values.add(count);
            }
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("range", range.getCode());
        data.put("bucket", bucketSec);
        data.put("points", points);
        data.put("times", times);
        data.put("values", values);
        if (qps) {
            data.put("requestValues", requestValues);
        }
        return data;
    }

    /**
     * 按窗口跨度 / 桶宽选标签：
     * 跨天窗口必须带日期，否则 24 小时时间轴会出现「22:48 → 22:24」这种看似倒序的标签；
     * 1 分钟内的高频桶必须带秒，否则整条时间轴全是同一个「HH:mm」。
     */
    private static DateTimeFormatter labelFormatter(long windowSeconds, long bucketSec) {
        if (windowSeconds >= 7 * 86400L) {
            return MM_DD;
        }
        if (windowSeconds >= 86400L) {
            return MM_DD_HH_MM;
        }
        if (bucketSec < 60) {
            return HH_MM_SS;
        }
        return HH_MM;
    }
}
