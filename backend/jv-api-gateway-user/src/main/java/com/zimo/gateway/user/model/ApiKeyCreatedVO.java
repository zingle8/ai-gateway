package com.zimo.gateway.user.model;

import java.time.LocalDateTime;

/**
 * 签发 / 轮换结果：fullKey = gwak-<keyId>-<secret>，明文仅此一次返回，服务端只存哈希
 */
public record ApiKeyCreatedVO(Long id, String keyId, String name, String secret,
                              String fullKey, LocalDateTime expireAt) {
}
