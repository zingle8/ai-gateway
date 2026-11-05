package com.zimo.gateway.user.model;

import jakarta.validation.constraints.NotBlank;

/**
 * 权限点新增 / 修改请求
 */
public record PermItemSaveRequest(
        @NotBlank String menuId,
        @NotBlank String funcName,
        @NotBlank String code,
        @NotBlank String name,
        String type,
        String api,
        Integer enabled) {
}
