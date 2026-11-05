package com.zimo.gateway.user.model;

import jakarta.validation.constraints.NotBlank;

/**
 * API Key 签发请求
 */
public record ApiKeySaveRequest(@NotBlank String name, Integer expireDays) {
}
