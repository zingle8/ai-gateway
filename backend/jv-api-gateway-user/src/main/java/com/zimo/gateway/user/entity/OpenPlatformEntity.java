package com.zimo.gateway.user.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 开放平台（第三方接入方）登记（表 tb_gw_open_platform）
 *
 * <p>平台以 appId 唯一标识；账号-平台授权关系见 {@link AccountPlatformEntity}。
 */
@Data
public class OpenPlatformEntity {

    private Long id;

    /** 平台标识（如 zimo-erp），开放认证校验时作为 appId 传入 */
    private String appId;

    private String appName;

    /** 1 启用，0 停用（停用后该平台的换 token 校验一律拒绝） */
    private Integer status;

    private String remark;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
