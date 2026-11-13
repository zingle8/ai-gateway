package com.zimo.gateway.user.model;

import jakarta.validation.constraints.NotBlank;

/**
 * 强制下线请求（五种粒度）
 *
 * <p>scope 取值：
 * <ul>
 *   <li>SESSION —— 单会话踢，需 sid</li>
 *   <li>PLATFORM —— 按平台踢，需 userId + appId（其他平台不受影响）</li>
 *   <li>DEVICE —— 按端踢（设备指纹），需 userId + device</li>
 *   <li>DEVICE_TYPE —— 按设备类型踢（手机 / 平板 / 电脑 / 服务端），需 userId + deviceType</li>
 *   <li>ALL —— 全端踢，需 userId（同时 token_version +1）</li>
 * </ul>
 *
 * @param scope      SESSION / PLATFORM / DEVICE / DEVICE_TYPE / ALL
 * @param sid        会话 ID（scope=SESSION）
 * @param userId     账号 ID（scope=PLATFORM/DEVICE/DEVICE_TYPE/ALL 或管理员操作时）
 * @param appId      平台标识（scope=PLATFORM）
 * @param device     设备指纹（scope=DEVICE）
 * @param deviceType 设备类型：PHONE / TABLET / PC / SERVER（scope=DEVICE_TYPE）
 * @param reason     下线原因（记入吊销记录）
 */
public record SessionKickRequest(@NotBlank(message = "下线粒度不能为空") String scope,
                                 String sid,
                                 Long userId,
                                 String appId,
                                 String device,
                                 String deviceType,
                                 String reason) {
}
