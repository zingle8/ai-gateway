package com.zimo.gateway.user.model;

import java.time.LocalDateTime;

/**
 * API Key 列表视图：不含任何密钥信息，明文只在签发 / 轮换响应里出现一次
 */
public record ApiKeyVO(Long id, String keyId, String name, Integer status,
                       LocalDateTime expireAt, LocalDateTime lastUsedAt, String lastUsedIp,
                       LocalDateTime createTime) {
}
