package com.zimo.gateway.config.model;

import jakarta.validation.constraints.NotBlank;

/**
 * 修改配置值
 */
public record ConfigUpdateRequest(@NotBlank(message = "配置值不能为空") String value) {
}
