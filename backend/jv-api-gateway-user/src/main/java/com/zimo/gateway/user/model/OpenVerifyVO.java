package com.zimo.gateway.user.model;

import com.zimo.gateway.user.entity.User;

import java.util.Date;

/**
 * 令牌校验结果（开放接口返回给第三方平台）
 *
 * @param valid   令牌是否有效
 * @param reason  无效原因（valid=true 时为 null）
 * @param userId  用户 ID
 * @param username 登录账号
 * @param nickname 姓名
 * @param role    控制台角色
 * @param expiresAt 令牌过期时间
 * @param permCodes 有效权限点（可选返回，便于第三方做按钮级控制）
 */
public record OpenVerifyVO(boolean valid, String reason, Long userId, String username, String nickname,
                           String role, Date expiresAt, java.util.List<String> permCodes) {

    public static OpenVerifyVO invalid(String reason) {
        return new OpenVerifyVO(false, reason, null, null, null, null, null, java.util.List.of());
    }

    public static OpenVerifyVO valid(User user, Date expiresAt, java.util.List<String> permCodes) {
        return new OpenVerifyVO(true, null, user.getId(), user.getUsername(), user.getNickname(),
                user.getRole(), expiresAt, permCodes);
    }
}
