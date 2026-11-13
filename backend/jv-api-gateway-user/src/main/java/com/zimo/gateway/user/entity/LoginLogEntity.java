package com.zimo.gateway.user.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 登录历史（表 tb_login_log，v0.2 第 13 项）。
 *
 * <p>成功与失败都落一条，失败时带连续失败次数；命中风险规则时同时写入
 * riskType / riskLevel / riskReason，供风控中心展示与后续统计。
 */
@Data
public class LoginLogEntity {

    private Long id;

    private String username;

    private Long userId;

    private String ip;

    private String userAgent;

    /** 归属平台：控制台 console，开放认证为接入方 appId */
    private String appId;

    /** 1 成功 0 失败 */
    private Integer success;

    private String failReason;

    /** 当次失败后的连续失败次数 */
    private Integer failCount;

    /** 命中多个风险时逗号分隔，如 LOGIN_NEW_IP,LOGIN_OFF_HOURS */
    private String riskType;

    private String riskLevel;

    private String riskReason;

    private LocalDateTime loginTime;
}
