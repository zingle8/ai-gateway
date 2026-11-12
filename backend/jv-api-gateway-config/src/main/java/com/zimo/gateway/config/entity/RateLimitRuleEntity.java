package com.zimo.gateway.config.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 网关限流规则（表 tb_gw_rate_limit）
 *
 * <p>令牌桶参数：每秒补充 replenishRate 个令牌，桶容量 burstCapacity，
 * 每次请求消耗 requestedTokens；桶满则丢弃多余令牌。
 */
@Data
public class RateLimitRuleEntity {

    private Long id;

    private String name;

    /** GLOBAL / ROUTE / API_KEY / IP */
    private String dimension;

    /** ROUTE 为 Ant 路径；API_KEY 为 keyId；IP 为具体 IP；其余为 * */
    private String target;

    /** 每秒补充令牌数（等效 QPS） */
    private Integer replenishRate;

    /** 桶容量（允许的突发量） */
    private Integer burstCapacity;

    /** 每次请求消耗令牌数 */
    private Integer requestedTokens;

    /** 1 启用，0 停用 */
    private Integer status;

    private String remark;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
