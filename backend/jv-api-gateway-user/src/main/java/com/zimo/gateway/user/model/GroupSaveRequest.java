package com.zimo.gateway.user.model;

import jakarta.validation.constraints.NotBlank;

/**
 * 分组新增 / 修改请求
 */
public record GroupSaveRequest(
        @NotBlank String name,
        @NotBlank String code,
        String type,
        Long parentId,
        String dataScope,
        String leader,
        String remark,
        Integer status) {
}
