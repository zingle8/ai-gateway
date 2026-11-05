package com.zimo.gateway.user.model;

import jakarta.validation.constraints.NotBlank;

/**
 * 注册请求：成功后直接返回登录令牌（注册即登录）
 */
public record RegisterRequest(
        @NotBlank String username,
        @NotBlank String password,
        String nickname) {
}
