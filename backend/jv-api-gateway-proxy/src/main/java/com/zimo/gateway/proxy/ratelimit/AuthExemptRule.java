package com.zimo.gateway.proxy.ratelimit;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 鉴权 / 限流豁免规则（proxy 侧只读视图）
 */
@Data
public class AuthExemptRule {

    private Long id;

    private String name;

    private String pathPattern;

    /** 逗号分隔的方法，* 表示全部 */
    private String methods;

    /** LOGIN / RATE / ALL */
    private String exemptType;

    private LocalDateTime expireAt;
}
