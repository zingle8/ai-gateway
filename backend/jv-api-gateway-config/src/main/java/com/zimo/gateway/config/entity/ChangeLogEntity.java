package com.zimo.gateway.config.entity;

import lombok.Data;

import java.time.LocalDateTime;

/** 配置 / 策略 / 路由变更审计（tb_gw_change_log） */
@Data
public class ChangeLogEntity {

    private Long id;

    /** CONFIG / POLICY / ROUTE */
    private String bizType;

    /** 配置键 / 策略键 / 路由前缀 */
    private String bizKey;

    /** CREATE / UPDATE / DELETE */
    private String action;

    private String beforeValue;

    private String afterValue;

    private String operator;

    private Long operatorId;

    private String requestIp;

    private LocalDateTime createTime;
}
