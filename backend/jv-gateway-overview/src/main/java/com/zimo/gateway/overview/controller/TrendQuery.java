package com.zimo.gateway.overview.controller;

import com.zimo.gateway.common.util.TimeRange;
import com.zimo.gateway.overview.model.StatsQuery;

import java.time.Duration;
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
 * 趋势分桶公共实现：网关总览的 QPS 趋势 与 数据统计的请求量趋势 共用。
 *
 * <p>SQL 由 OverviewMapper.trendBuckets 执行，本类负责构造查询条件，
 * 以及把稀疏的桶补齐成 {@link TimeRange#POINTS} 个连续点位。
 * 桶对齐用 epoch 秒整除（分钟取模在跨小时 / 跨天时会错位）。
 */
final class TrendQuery {

    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter HH_MM_SS = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter MM_DD_HH_MM = DateTimeFormatter.ofPattern("MM-dd HH:mm");
    private static final DateTimeFormatter MM_DD = DateTimeFormatter.ofPattern("MM-dd");

    private TrendQuery() {
    }

    /** 时间窗跨度（分钟）：1 天取「今日 00:00 至今」，其余按区间固定的跨度 */
    static int windowMinutes(TimeRange range) {
        if (range == TimeRange.D1) {
            return (int) Math.max(1, Duration.between(LocalDate.now().atStartOfDay(), LocalDateTime.now()).toMinutes());
        }
        return (int) range.getWindowMinutes();
    }

    /** 构造趋势查询条件；统计侧可在此基础上继续设置 appId / serviceName / api / methodLike */
    static StatsQuery query(TimeRange range) {
        StatsQuery query = new StatsQuery();
        query.setBucketSec((long) range.getBucketSeconds());
        query.setWindowMinutes(windowMinutes(range));
        return query;
    }

    /**
     * 把 mapper 返回的稀疏桶补成连续点位（固定 {@link TimeRange#POINTS} 个，与区间档位无关）。
     *
     * @param rows mapper 结果，每行含 b（桶起点 epoch 秒）、c（桶内请求数）
     * @param qps  true = 桶内请求数 ÷ 桶宽秒数（总览 QPS），false = 桶内请求量（数据统计）
     */
    static Map<String, Object> build(List<Map<String, Object>> rows, TimeRange range, boolean qps) {
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
        for (int i = points - 1; i >= 0; i--) {
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
        data.put("range", range.getCode());
        data.put("bucket", bucketSec);
        data.put("points", points);
        data.put("times", times);
        data.put("values", values);
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
