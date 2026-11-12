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

    /** 调用方 IP 白名单（逗号分隔，支持 10.20.3.* 前缀通配；空 = 不限） */
    private String allowedIps;

    /** 可用服务白名单（tb_gw_route.service_name，逗号分隔；空 = 不限） */
    private String allowedServices;

    /** 可用平台白名单（tb_gw_open_platform.app_id，逗号分隔；空 = 不限） */
    private String allowedPlatforms;

    /** 该 Key 单独的每分钟调用上限（0 = 不单独限流） */
    private Integer rateLimitPerMinute;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
