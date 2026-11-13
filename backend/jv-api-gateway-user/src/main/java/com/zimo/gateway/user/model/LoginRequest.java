package com.zimo.gateway.user.model;

import lombok.Data;

/**
 * 登录请求（多因子，v0.2 第 4 项）。
 *
 * <p>username / password 不再用 @NotBlank：QR_CONFIRM（扫码确认流）只有 ticket，
 * 各因子的必填校验在 {@code AuthController#login} 按因子分派时完成。
 */
@Data
public class LoginRequest {

    /** 账号：PASSWORD / SMS / EMAIL / QR 必填，QR_CONFIRM 可空 */
    private String username;

    /** 密码：仅 PASSWORD 因子必填 */
    private String password;

    /**
     * 登录因子：PASSWORD（默认）/ SMS / EMAIL / QR / QR_CONFIRM
     */
    private String factor;

    /** 验证码 / 动态码 / 扫码会话标识（由 /auth/captcha/*、/auth/otp/send、/auth/qr/create 下发） */
    private String ticket;

    /** 验证码 / 动态码 */
    private String code;

    /** 归属平台：控制台登录留空（console），开放认证由接入方传入 appId */
    private String appId;
}
