package com.zimo.gateway.user.controller;

import com.zimo.gateway.common.result.R;
import com.zimo.gateway.common.util.IpUtil;
import com.zimo.gateway.user.model.LoginRequest;
import com.zimo.gateway.user.model.LoginVO;
import com.zimo.gateway.user.model.OpenVerifyRequest;
import com.zimo.gateway.user.model.OpenVerifyVO;
import com.zimo.gateway.user.model.RegisterRequest;
import com.zimo.gateway.user.service.OpenAuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 开放认证接口：给「其他平台」对接用，网关白名单放行（/api/open/auth/**），
 * 是否需要开放由系统设置 platform.open.auth.enabled 控制。
 *
 * <p>与平台自身 /auth/* 的区别：这里的接口语义稳定、返回值面向第三方，
 * 且不做控制台会话相关处理（不写在线会话、不退出登录）。
 */
@RestController
@RequestMapping("/open/auth")
@RequiredArgsConstructor
public class OpenAuthController {

    private final OpenAuthService openAuthService;

    /** 注册（默认 VIEWER），成功即返回令牌 */
    @PostMapping("/register")
    public R<LoginVO> register(@Valid @RequestBody RegisterRequest request, HttpServletRequest httpRequest) {
        return R.ok(openAuthService.register(request, IpUtil.get(httpRequest)));
    }

    /** 登录，返回令牌与基础用户信息 */
    @PostMapping("/login")
    public R<LoginVO> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        return R.ok(openAuthService.login(request, IpUtil.get(httpRequest)));
    }

    /** 校验令牌：供第三方平台在每次请求前判断用户是否仍有效 */
    @PostMapping("/token/verify")
    public R<OpenVerifyVO> verify(@Valid @RequestBody OpenVerifyRequest request) {
        return R.ok(openAuthService.verify(request.token()));
    }
}
