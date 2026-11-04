package com.zimo.gateway.overview.controller;

import com.zimo.gateway.common.util.TimeRange;
import com.zimo.gateway.overview.model.StatsQuery;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 趋势查询条件构造：网关总览的 QPS 趋势 与 数据统计的请求量趋势 共用。
 *
 * <p>分桶补点逻辑在公共模块 {@link com.zimo.gateway.common.util.TrendBuckets}
 * （服务发现的趋势图也复用），本类只负责构造 OverviewMapper 需要的查询条件。
 */
final class TrendQuery {

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

    /** 委托公共分桶实现（qps=true 时同时返回 QPS 与请求量两套序列） */
    static Map<String, Object> build(List<Map<String, Object>> rows, TimeRange range, boolean qps) {
        return com.zimo.gateway.common.util.TrendBuckets.build(rows, range, qps);
    }
}
