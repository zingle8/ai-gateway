package com.zimo.gateway.user.model;

import jakarta.validation.constraints.NotBlank;

/**
 * API Key 签发请求。
 *
 * <p>v0.2 第 6 项起可同时带上调用方约束（IP / 服务 / 平台白名单 + 每分钟限流），
 * 也可签发后在「API Key」页通过「约束」对话框单独调整（PUT /keys/{id}/scope）。
 */
public record ApiKeySaveRequest(@NotBlank String name, Integer expireDays,
                               String allowedIps, String allowedServices, String allowedPlatforms,
                               Integer rateLimitPerMinute) {

    /** 兼容旧调用：只关心名称与有效期 */
    public ApiKeySaveRequest(String name, Integer expireDays) {
        this(name, expireDays, null, null, null, null);
    }
}
