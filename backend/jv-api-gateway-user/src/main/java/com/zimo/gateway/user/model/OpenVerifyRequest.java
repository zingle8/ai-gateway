package com.zimo.gateway.user.model;

import jakarta.validation.constraints.NotBlank;

/**
 * 第三方平台校验令牌
 *
 * @param token 待校验令牌：用户 JWT（eyJ...）或 API Key（gwak-...）
 * @param type  令牌类型：USER / API_KEY / AUTO（默认，按前缀自动识别）
 * @param appId 目标平台标识（可选）：传入时校验平台存在且启用、账号已授权该平台——
 *              「认证了 ≠ 能用」，防用户令牌跨平台横向使用；不传则保持原有行为
 */
public record OpenVerifyRequest(@NotBlank(message = "token 不能为空") String token, String type, String appId) {
}
