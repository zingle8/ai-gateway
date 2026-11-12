package com.zimo.gateway.proxy.auth;

import java.util.List;

/**
 * API Key 调用方约束（v0.2 第 6 项）。
 *
 * <p>列表为空 = 不限制；rateLimitPerMinute = 0 = 不单独限流。
 * 由 {@link AuthVerifier} 在 Key 校验通过后随裁决结果带出，
 * 供 {@link com.zimo.gateway.proxy.scope.ApiKeyScopeFilter}（服务 / 平台白名单）
 * 与 {@link com.zimo.gateway.proxy.ratelimit.RateLimitGlobalFilter}（每分钟限流）消费。
 */
public record ApiKeyScope(String keyId, List<String> allowedServices, List<String> allowedPlatforms,
                          int rateLimitPerMinute) {

    /** 鉴权通过后写入 exchange 的属性名：避免后续过滤器重复解析 / 查库 */
    public static final String ATTR = "gw.auth.apiKeyScope";

    public boolean restrictsService() {
        return allowedServices != null && !allowedServices.isEmpty();
    }

    public boolean restrictsPlatform() {
        return allowedPlatforms != null && !allowedPlatforms.isEmpty();
    }

    public boolean hasRateLimit() {
        return rateLimitPerMinute > 0;
    }
}
