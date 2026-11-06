package com.zimo.gateway.overview.model;

import lombok.Data;

/**
 * 分布 / Top 榜的一行：状态码分布、方法分布、Top 服务、Top 接口。
 *
 * <p>字段名直接对应前端消费的 JSON，无需在 Controller 里二次转换。
 */
@Data
public class StatsRow {

    private String name;

    /** 归属应用编码 / 应用名（Top 榜展示用） */
    private String appId;

    private String appName;

    /** 服务中文名（Top 接口可能跨多个服务，用「、」连接；Top 服务为单个中文名） */
    private String serviceLabel;

    private Long count;

    private Double avgRt;

    private Double errorRate;
}
