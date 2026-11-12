package com.zimo.gateway.user.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 账号-开放平台授权关系（表 tb_gw_account_platform）
 *
 * <p>一个账号可授权到 N 个平台；开放认证换 token 时若指定 appId，
 * 校验该账号是否存在本表记录（认证了 ≠ 能用）。
 */
@Data
public class AccountPlatformEntity {

    private Long id;

    private Long userId;

    /** 平台标识，对应 tb_gw_open_platform.app_id */
    private String appId;

    private LocalDateTime createTime;
}
