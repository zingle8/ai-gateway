package com.zimo.gateway.user.model;

/**
 * 在线会话（登录时登记，Redis 存储）
 *
 * @param sid        会话 ID（JWT 的 sid claim，踢人时按它精确失效）
 * @param userId     账号 ID
 * @param username   登录账号
 * @param nickname   姓名
 * @param ip         登录 IP
 * @param device     设备指纹（由 UA 归一化：Chrome / macOS / Windows / App 等）
 * @param ua         原始 User-Agent
 * @param appId      归属平台（控制台登录为 console，开放认证登录为接入方 appId）
 * @param loginTime  登录时间（毫秒）
 * @param lastActive 最近活跃时间（毫秒）
 * @param onlineSec  已在线秒数
 */
public record SessionVO(String sid, Long userId, String username, String nickname,
                        String ip, String device, String ua, String appId,
                        long loginTime, long lastActive, long onlineSec) {
}
