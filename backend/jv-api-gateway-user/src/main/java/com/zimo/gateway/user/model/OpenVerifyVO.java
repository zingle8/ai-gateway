package com.zimo.gateway.user.model;

import java.util.Date;
import java.util.List;

/**
 * 令牌校验结果（开放接口返回给第三方平台）
 *
 * <p>支持两类令牌：
 * <ul>
 *   <li>USER —— 用户 JWT：返回用户身份、角色、权限点、过期时间</li>
 *   <li>API_KEY —— 应用级 API Key：返回 keyId / keyName / 过期时间（无用户身份）</li>
 * </ul>
 *
 * @param valid    令牌是否有效
 * @param tokenType USER / API_KEY
 * @param reason   无效原因（valid=true 时为 null）
 * @param userId   用户 ID（API_KEY 时为空）
 * @param username 登录账号（API_KEY 时为空）
 * @param nickname 姓名（API_KEY 时为空）
 * @param role     控制台角色（API_KEY 时为空）
 * @param keyId     API Key 短标识（USER 时为空）
 * @param keyName   API Key 名称（USER 时为空）
 * @param expiresAt 过期时间
 * @param permCodes 有效权限点（仅 USER，便于第三方做按钮级控制）
 */
public record OpenVerifyVO(boolean valid, String tokenType, String reason,
                           Long userId, String username, String nickname, String role,
                           String keyId, String keyName,
                           Date expiresAt, List<String> permCodes) {

    public static OpenVerifyVO invalid(String tokenType, String reason) {
        return new OpenVerifyVO(false, tokenType, reason, null, null, null, null, null, null, null, List.of());
    }

    public static OpenVerifyVO validUser(com.zimo.gateway.user.entity.UserEntity user,
                                         Date expiresAt, List<String> permCodes) {
        return new OpenVerifyVO(true, "USER", null, user.getId(), user.getUsername(), user.getNickname(),
                user.getRole(), null, null, expiresAt, permCodes);
    }

    public static OpenVerifyVO validKey(com.zimo.gateway.user.entity.ApiKeyEntity key) {
        return new OpenVerifyVO(true, "API_KEY", null, null, null, null, null,
                key.getKeyId(), key.getName(),
                key.getExpireAt() == null ? null
                        : Date.from(key.getExpireAt().atZone(java.time.ZoneId.systemDefault()).toInstant()),
                List.of());
    }
}
