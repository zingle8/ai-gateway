package com.zimo.gateway.user.controller;

import com.zimo.gateway.common.context.UserContext;
import com.zimo.gateway.common.exception.BizException;
import com.zimo.gateway.common.result.R;
import com.zimo.gateway.common.result.ResultCode;
import com.zimo.gateway.common.util.IpUtil;
import com.zimo.gateway.user.entity.UserEntity;
import com.zimo.gateway.user.model.LoginRequest;
import com.zimo.gateway.user.model.LoginVO;
import com.zimo.gateway.user.model.RegisterRequest;
import com.zimo.gateway.user.service.LoginFactorService;
import com.zimo.gateway.user.service.LoginPolicyService;
import com.zimo.gateway.user.service.PermService;
import com.zimo.gateway.user.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 认证接口：注册 / 登录 / 退出 / 当前用户 / 我的有效权限
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;
    private final PermService permService;
    private final LoginFactorService factorService;
    private final LoginPolicyService loginPolicyService;

    /**
     * 注册：默认 VIEWER 角色，成功即登录（返回令牌）
     */
    @PostMapping("/register")
    public R<LoginVO> register(@Valid @RequestBody RegisterRequest request, HttpServletRequest httpRequest) {
        return R.ok(userService.register(request, IpUtil.get(httpRequest)));
    }

    /**
     * 登录（多因子，v0.2 第 4 项）
     *
     * <p>factor = PASSWORD（默认，开启验证码时校验图形/动态码）/ SMS / EMAIL / QR（扫码取码流）/
     * QR_CONFIRM（扫码确认流，ticket 已被手机端确认）。各因子的开关读「登录方式」页配置。
     */
    @PostMapping("/login")
    public R<LoginVO> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        String ua = httpRequest.getHeader("User-Agent");
        String appId = request.getAppId() == null || request.getAppId().isBlank()
                ? "console" : request.getAppId().trim();
        String ip = IpUtil.get(httpRequest);
        String factor = request.getFactor() == null || request.getFactor().isBlank()
                ? "PASSWORD" : request.getFactor().trim().toUpperCase();

        switch (factor) {
            case "PASSWORD" -> {
                if (request.getUsername() == null || request.getUsername().isBlank()) {
                    throw new BizException(ResultCode.BAD_REQUEST.getCode(), "账号不能为空");
                }
                if (request.getPassword() == null || request.getPassword().isBlank()) {
                    throw new BizException(ResultCode.BAD_REQUEST.getCode(), "密码不能为空");
                }
                if (loginPolicyService.captchaEnabled()) {
                    factorService.verifyCaptcha(request.getTicket(), request.getCode());
                }
                return R.ok(userService.login(request.getUsername(), request.getPassword(), ip, ua, appId));
            }
            case "SMS", "EMAIL" -> {
                if (!loginPolicyService.factorEnabled("EMAIL".equals(factor) ? "emailOtp" : "smsOtp")) {
                    throw new BizException(ResultCode.BAD_REQUEST.getCode(),
                            ("EMAIL".equals(factor) ? "邮件" : "短信") + "验证码登录方式已关闭");
                }
                UserEntity user = factorService.verifyOtp(request.getTicket(), request.getCode());
                return R.ok(userService.loginByFactor(user, ip, ua, appId, factor));
            }
            case "QR" -> {
                if (!loginPolicyService.factorEnabled("qrcode")) {
                    throw new BizException(ResultCode.BAD_REQUEST.getCode(), "扫码登录方式已关闭");
                }
                factorService.verifyQrCode(request.getTicket(), request.getCode());
                UserEntity user = factorService.findByUsername(request.getUsername());
                return R.ok(userService.loginByFactor(user, ip, ua, appId, "QR"));
            }
            case "QR_CONFIRM" -> {
                if (!loginPolicyService.factorEnabled("qrcode")) {
                    throw new BizException(ResultCode.BAD_REQUEST.getCode(), "扫码登录方式已关闭");
                }
                UserEntity user = factorService.consumeQrConfirmation(request.getTicket());
                return R.ok(userService.loginByFactor(user, ip, ua, appId, "QR_CONFIRM"));
            }
            default -> throw new BizException(ResultCode.BAD_REQUEST.getCode(), "不支持的登录方式：" + factor);
        }
    }

    /**
     * 退出登录
     */
    @PostMapping("/logout")
    public R<Void> logout() {
        userService.logout(UserContext.getUserId());
        return R.ok();
    }

    /**
     * 当前登录用户信息
     */
    @GetMapping("/info")
    public R<LoginVO> info() {
        UserEntity user = userService.getById(UserContext.getUserId());
        if (user == null) {
            return R.fail(ResultCode.UNAUTHORIZED.getCode(), "用户不存在");
        }
        user.setPassword(null);
        return R.ok(LoginVO.of(user));
    }

    /**
     * 当前用户的有效权限点：所属全部分组的权限并集，前端用于菜单 / 按钮可见性
     */
    @GetMapping("/my-perms")
    public R<List<String>> myPerms() {
        return R.ok(permService.permsOfUser(UserContext.getUserId()));
    }
}
