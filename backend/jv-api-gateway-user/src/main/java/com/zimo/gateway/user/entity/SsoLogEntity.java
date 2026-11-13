package com.zimo.gateway.user.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * SSO 登录审计（表 tb_gw_sso_log，v0.2 第 15 项）。
 */
@Data
public class SsoLogEntity {

    private Long id;

    private Long userId;

    private String username;

    private String clientId;

    private String appId;

    /** AUTHORIZE / TOKEN / USERINFO / LOGOUT */
    private String action;

    /** SUCCESS / DENIED / ERROR */
    private String result;

    private String ip;

    private String detail;

    private LocalDateTime createTime;
}
