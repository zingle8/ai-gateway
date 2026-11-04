package com.zimo.gateway.discovery.model;

import lombok.Data;

/**
 * 服务分页查询条件
 */
@Data
public class ServiceQuery {

    /** 关键字：应用ID / 应用名 / 服务名 */
    private String keyword;

    /** 应用编码 */
    private String appId;

    /** 服务名 */
    private String serviceName;

    /** 服务状态：健康 / 亚健康 / 异常 */
    private String status;

    /** 语言类型 */
    private String language;

    /**
     * 运行指标统计窗口（秒）：由统一时间区间（1m ~ 30d）换算而来，
     * 决定列表里平均耗时 / 流量按多长时间聚合，默认 300 秒（近 5 分钟）。
     */
    private int windowSeconds = 300;

    private long current = 1;

    private long size = 10;
}
