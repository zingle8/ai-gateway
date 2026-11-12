package com.zimo.gateway.proxy.ratelimit;

import lombok.Data;

/**
 * 网关限流规则（proxy 侧只读视图，表 tb_gw_rate_limit）
 */
@Data
public class RateLimitRule {

    private Long id;

    private String name;

    /** GLOBAL / ROUTE / API_KEY / IP */
    private String dimension;

    /** ROUTE 为 Ant 路径；API_KEY 为 keyId；IP 为具体 IP；其余为 * */
    private String target;

    private Integer replenishRate;

    private Integer burstCapacity;

    private Integer requestedTokens;

    private Integer status;
}
