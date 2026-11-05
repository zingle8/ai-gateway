package com.zimo.gateway.user.model;

import com.zimo.gateway.user.entity.UserEntity;

/**
 * 登录结果 / 当前用户信息
 */
public record LoginVO(String token, Long id, String username, String nickname, String role) {

    public static LoginVO of(String token, UserEntity user) {
        return new LoginVO(token, user.getId(), user.getUsername(), user.getNickname(), user.getRole());
    }

    public static LoginVO of(UserEntity user) {
        return of(null, user);
    }
}
