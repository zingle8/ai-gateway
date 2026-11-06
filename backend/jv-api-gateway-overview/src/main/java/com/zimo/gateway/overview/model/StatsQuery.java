package com.zimo.gateway.overview.model;

import lombok.Data;

/**
 * 数据统计的动态查询条件（供 OverviewMapper.xml 的 statsFilters 片段使用）。
 *
 * <p>时间窗口二选一：summary 用 hours（近 N 小时），trend 用 windowMinutes（粒度 × 120 分钟）。
 */
@Data
public class StatsQuery {

    private String appId;

    /** 服务ID（英文唯一标识，对应 tb_gw_trace_log.service_name 值） */
    private String serviceId;

    private String api;

    /** 方法前缀匹配值，如 "GET %"；api 形如 "GET /api/xxx" */
    private String methodLike;

    /** summary / 分布 / Top 榜：近 N 小时 */
    private Integer hours;

    /** trend：近 N 分钟 */
    private Integer windowMinutes;

    /** trend：桶宽秒数 */
    private Long bucketSec;

    /** 分位查询的排序偏移量 */
    private Integer offset;
}
