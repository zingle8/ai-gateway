package com.zimo.gateway.user.controller;

import com.zimo.gateway.common.context.UserContext;
import com.zimo.gateway.common.result.R;
import com.zimo.gateway.common.result.ResultCode;
import com.zimo.gateway.common.util.IpUtil;
import com.zimo.gateway.user.entity.User;
import com.zimo.gateway.user.model.LoginRequest;
import com.zimo.gateway.user.model.LoginVO;
import com.zimo.gateway.user.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证接口：登录 / 退出 / 当前用户
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;

    /**
     * 登录
     */
    @PostMapping("/login")
    public R<LoginVO> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        return R.ok(userService.login(request.getUsername(), request.getPassword(), IpUtil.get(httpRequest)));
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
        User user = userService.getById(UserContext.getUserId());
        if (user == null) {
            return R.fail(ResultCode.UNAUTHORIZED.getCode(), "用户不存在");
        }
        user.setPassword(null);
        return R.ok(LoginVO.of(user));
    }
}
