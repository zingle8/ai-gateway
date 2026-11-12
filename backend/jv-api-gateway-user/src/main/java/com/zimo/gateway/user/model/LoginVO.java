package com.zimo.gateway.user.model;

import com.zimo.gateway.user.entity.UserEntity;

/**
 * 登录结果 / 当前用户信息
 */
public record LoginVO(String token, Long id, String username, String nickname, String role, boolean mfaRequired) {

    public static LoginVO of(String token, UserEntity user) {
        return of(token, user, false);
    }

    /** mfaRequired=true 时 token 为 null：前端提示完成二次验证后再登录（v0.2 先提示，TOTP 校验随 v0.3） */
    public static LoginVO of(String token, UserEntity user, boolean mfaRequired) {
        return new LoginVO(token, user.getId(), user.getUsername(), user.getNickname(), user.getRole(), mfaRequired);
    }

    public static LoginVO of(UserEntity user) {
        return of(null, user);
    }
}
