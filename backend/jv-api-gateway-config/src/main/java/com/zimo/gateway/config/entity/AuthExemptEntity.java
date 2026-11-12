package com.zimo.gateway.config.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 鉴权 / 限流精细豁免规则（表 tb_gw_auth_exempt）
 *
 * <p>网关以「追加」方式应用：yml 内置白名单始终有效，本表规则只是额外放行，
 * 避免误配置导致登录等关键链路不可用。
 */
@Data
public class AuthExemptEntity {

    private Long id;

    private String name;

    /** Ant 风格路径，如 /api/pay/callback/** */
    private String pathPattern;

    /** 允许的方法，逗号分隔；* 表示全部 */
    private String methods;

    /** LOGIN 免登录 / RATE 免限流 / ALL 两者都免 */
    private String exemptType;

    /** 1 启用，0 停用 */
    private Integer status;

    /** 到期时间（NULL = 永久） */
    private LocalDateTime expireAt;

    private String remark;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
