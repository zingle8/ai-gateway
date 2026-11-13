package com.zimo.gateway.user.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 风控事件（表 tb_gw_risk_event，v0.2 第 13 项）。
 *
 * <p>用户（登录异常）与 API Key（调用异常 / 临期 / 长期未用）统一登记在一张表，
 * 处置动作与操作人、备注、时间直接落在事件行上，构成处置留痕。
 */
@Data
public class RiskEventEntity {

    private Long id;

    /** USER / API_KEY */
    private String subjectType;

    /** 用户 ID 或 API Key 短标识 */
    private String subjectId;

    /** 账号 / Key 名称 */
    private String subjectName;

    /**
     * LOGIN_NEW_IP / LOGIN_OFF_HOURS / LOGIN_BRUTE_FORCE /
     * KEY_SPIKE / KEY_ERROR_RATE / KEY_EXPIRING / KEY_UNUSED
     */
    private String riskType;

    /** HIGH / MEDIUM / LOW */
    private String riskLevel;

    private String detail;

    /** OPEN 待处置 / HANDLED 已处置 / IGNORED 已忽略 */
    private String status;

    /** ACK / DISABLE_USER / ENABLE_USER / UNLOCK_USER / KICK_USER / DISABLE_KEY / ENABLE_KEY */
    private String action;

    private String handler;

    private String handleRemark;

    private LocalDateTime handleTime;

    private LocalDateTime createTime;
}
