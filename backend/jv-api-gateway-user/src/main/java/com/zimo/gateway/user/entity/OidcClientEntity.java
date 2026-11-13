package com.zimo.gateway.user.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * SSO 接入方（OIDC Client）登记（表 tb_gw_oidc_client，v0.2 第 15 项）。
 *
 * <p>client_secret 库内只存 SHA-256 摘要，原文仅在签发 / 轮换时返回一次；
 * app_id 关联 tb_gw_open_platform，换 token 时校验「账号-平台授权关系」（第 7 项）。
 */
@Data
public class OidcClientEntity {

    private Long id;

    private String clientId;

    /** SHA-256 十六进制摘要 */
    private String clientSecret;

    private String clientName;

    /** 关联开放平台 appId；为空表示不校验账号-平台授权（仅控制台账号即可） */
    private String appId;

    /** 回调地址，多个逗号分隔，精确匹配 */
    private String redirectUri;

    private String scopes;

    /** 1 启用 0 停用 */
    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
