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

    private Long count;

    private Double avgRt;

    private Double errorRate;
}
