package com.zimo.gateway.user.model;

import jakarta.validation.constraints.NotBlank;

/**
 * 强制下线请求（四种粒度）
 *
 * <p>scope 取值：
 * <ul>
 *   <li>SESSION —— 单会话踢，需 sid</li>
 *   <li>PLATFORM —— 按平台踢，需 userId + appId（其他平台不受影响）</li>
 *   <li>DEVICE —— 按端踢，需 userId + device</li>
 *   <li>ALL —— 全端踢，需 userId（同时 token_version +1）</li>
 * </ul>
 *
 * @param scope  SESSION / PLATFORM / DEVICE / ALL
 * @param sid    会话 ID（scope=SESSION）
 * @param userId 账号 ID（scope=PLATFORM/DEVICE/ALL 或管理员操作时）
 * @param appId  平台标识（scope=PLATFORM）
 * @param device 设备指纹（scope=DEVICE）
 * @param reason 下线原因（记入吊销记录）
 */
public record SessionKickRequest(@NotBlank(message = "下线粒度不能为空") String scope,
                                 String sid,
                                 Long userId,
                                 String appId,
                                 String device,
                                 String reason) {
}
