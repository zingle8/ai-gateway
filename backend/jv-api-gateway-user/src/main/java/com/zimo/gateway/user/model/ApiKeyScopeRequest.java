package com.zimo.gateway.user.model;

/**
 * API Key 调用方约束保存请求（全量覆盖）。
 *
 * <p>空字符串 / null 表示不限；rateLimitPerMinute 为 0 表示不单独限流。
 * 网关侧 30 秒内生效（与鉴权缓存 TTL 一致）。
 */
public record ApiKeyScopeRequest(String allowedIps, String allowedServices, String allowedPlatforms,
                                 Integer rateLimitPerMinute) {
}
