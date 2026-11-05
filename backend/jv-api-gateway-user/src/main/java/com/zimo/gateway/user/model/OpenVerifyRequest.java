package com.zimo.gateway.user.model;

import jakarta.validation.constraints.NotBlank;

/**
 * 第三方平台校验令牌
 */
public record OpenVerifyRequest(@NotBlank(message = "token 不能为空") String token) {
}
