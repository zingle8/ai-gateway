package com.zimo.gateway.user.model;

import java.time.LocalDateTime;

/**
 * API Key 列表视图：不含任何密钥信息，明文只在签发 / 轮换响应里出现一次。
 *
 * <p>allowedIps / allowedServices / allowedPlatforms 为空串表示不限；
 * rateLimitPerMinute 为 0 表示不单独限流。
 */
public record ApiKeyVO(Long id, String keyId, String name, Integer status,
                       LocalDateTime expireAt, LocalDateTime lastUsedAt, String lastUsedIp,
                       String allowedIps, String allowedServices, String allowedPlatforms,
                       Integer rateLimitPerMinute, LocalDateTime createTime) {

    /** 兼容仅需状态字段的局部更新（PUT /keys/{id}/status） */
    public ApiKeyVO(Long id, String keyId, String name, Integer status,
                    LocalDateTime expireAt, LocalDateTime lastUsedAt, String lastUsedIp,
                    LocalDateTime createTime) {
        this(id, keyId, name, status, expireAt, lastUsedAt, lastUsedIp, null, null, null, null, createTime);
    }
}
