package com.zimo.gateway.user.controller;

import com.zimo.gateway.common.result.R;
import com.zimo.gateway.user.service.LoginFactorService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 登录因子接口（v0.2 第 4 项）：图形验证码 / 弹框动态码 / 短信·邮件动态码 / 扫码登录。
 *
 * <p>这些接口都在登录前调用，已在网关 skip-paths 中放行（无需鉴权）。
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class LoginFactorController {

    private final LoginFactorService factorService;

    /**
     * 登录方式公开视图（登录前渲染因子入口用）：只含因子开关与验证码 / 扫码形态，
     * 不含 SMTP / 短信通道凭据（通道凭据在控制台「登录方式」页经鉴权接口维护）。
     */
    @GetMapping("/login-methods")
    public R<Map<String, Object>> publicLoginConfig() {
        return R.ok(factorService.publicLoginConfig());
    }

    /** 图形验证码（base64 PNG，120 秒） */
    @GetMapping("/captcha/image")
    public R<Map<String, Object>> imageCaptcha() {
        return R.ok(factorService.createImageCaptcha());
    }

    /** 弹框动态码（6 位，30 秒；前端到点自动换） */
    @GetMapping("/captcha/dynamic")
    public R<Map<String, Object>> dynamicCode() {
        return R.ok(factorService.createDynamicCode());
    }

    /**
     * 发送短信 / 邮件动态码（模拟通道，验证码写服务日志）
     * body: { username, channel: SMS | EMAIL }
     */
    @PostMapping("/otp/send")
    public R<Map<String, Object>> sendOtp(@RequestBody Map<String, Object> body) {
        String username = body.get("username") == null ? null : String.valueOf(body.get("username"));
        String channel = body.get("channel") == null ? "SMS" : String.valueOf(body.get("channel"));
        return R.ok(factorService.sendOtp(username, channel));
    }

    /**
     * 创建扫码会话
     * body: { mode: confirm | code }，content / code 二选一返回
     */
    @PostMapping("/qr/create")
    public R<Map<String, Object>> createQr(@RequestBody(required = false) Map<String, Object> body,
                                           HttpServletRequest request) {
        String mode = body == null || body.get("mode") == null ? null : String.valueOf(body.get("mode"));
        String origin = request.getHeader("Origin");
        if (origin == null || origin.isBlank()) {
            String referer = request.getHeader("Referer");
            if (referer != null && referer.contains("://")) {
                origin = referer.substring(0, referer.indexOf('/', referer.indexOf("://") + 3));
            }
        }
        return R.ok(factorService.createQr(mode, origin));
    }

    /** 扫码取码流的动态码刷新（30 秒） */
    @GetMapping("/qr/code")
    public R<Map<String, Object>> refreshQrCode(@RequestParam String ticket) {
        return R.ok(factorService.refreshQrCode(ticket));
    }

    /** 扫码状态轮询：WAITING / CONFIRMED / EXPIRED */
    @GetMapping("/qr/status")
    public R<Map<String, Object>> qrStatus(@RequestParam String ticket) {
        return R.ok(factorService.qrStatus(ticket));
    }

    /**
     * 手机端确认（演示确认流）
     * body: { ticket, username, password }
     */
    @PostMapping("/qr/confirm")
    public R<Map<String, Object>> confirmQr(@RequestBody Map<String, Object> body) {
        String ticket = body.get("ticket") == null ? null : String.valueOf(body.get("ticket"));
        String username = body.get("username") == null ? null : String.valueOf(body.get("username"));
        String password = body.get("password") == null ? null : String.valueOf(body.get("password"));
        return R.ok(factorService.confirmQr(ticket, username, password));
    }
}
