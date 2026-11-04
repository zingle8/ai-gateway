package com.zimo.gateway.overview.model;

import lombok.Data;

/**
 * 调用日志聚合结果（总览「今日」与数据统计「近 N 小时」共用）。
 */
@Data
public class StatsAgg {

    private Long total;

    private Long errors;

    private Double avgRt;

    private Long reqBytes;

    private Long resBytes;
}
