package com.zimo.gateway.user.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * IP 封禁记录（tb_gw_ip_ban）
 */
@Data
public class IpBanEntity {

    private Long id;

    /** 被封禁的 IP（支持前缀通配 10.0.0.*） */
    private String ip;

    /** 封禁原因 */
    private String reason;

    /** 操作人（system = 自动） */
    private String operator;

    /** 1 生效 / 0 已解封 */
    private Integer status;

    private LocalDateTime bannedAt;

    private LocalDateTime unbannedAt;
}
