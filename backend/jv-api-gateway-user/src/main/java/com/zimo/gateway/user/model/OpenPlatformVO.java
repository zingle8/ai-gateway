package com.zimo.gateway.user.model;

/**
 * 开放平台清单项（管理端展示用）
 *
 * @param id      自增主键
 * @param appId   平台标识（开放认证校验时作为 appId 传入）
 * @param appName 平台名称
 * @param status  1 启用 / 0 停用
 * @param remark  备注
 * @param granted 该平台是否已授权给目标账号（查授权时才有意义，清单接口返回 false）
 */
public record OpenPlatformVO(Long id, String appId, String appName, Integer status, String remark, boolean granted) {
}
