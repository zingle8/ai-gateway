package com.zimo.gateway.user.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 网关 API Key（表 tb_gw_api_key）
 * 只存 SHA-256(secret)，明文仅签发 / 轮换时返回一次。
 */
@Data
public class ApiKeyEntity {

    private Long id;

    /** 短标识，出现在明文 Key 中用于定位记录（gwak-<keyId>-<secret>） */
    private String keyId;

    /** SHA-256(secret) 十六进制 */
    private String secretHash;

    private String name;

    private Long ownerUserId;

    /** 1 启用，0 停用 */
    private Integer status;

    private LocalDateTime expireAt;

    private LocalDateTime lastUsedAt;

    private String lastUsedIp;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
