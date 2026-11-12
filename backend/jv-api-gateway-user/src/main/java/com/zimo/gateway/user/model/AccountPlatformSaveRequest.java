package com.zimo.gateway.user.model;

import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * 账号-平台授权保存请求（全量覆盖）
 *
 * @param appIds 授权给该账号的平台 appId 列表；传空数组表示清空授权
 */
public record AccountPlatformSaveRequest(@NotNull(message = "appIds 不能为 null") List<String> appIds) {
}
