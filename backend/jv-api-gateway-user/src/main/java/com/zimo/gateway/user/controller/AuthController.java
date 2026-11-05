package com.zimo.gateway.user.controller;

import com.zimo.gateway.common.context.UserContext;
import com.zimo.gateway.common.result.R;
import com.zimo.gateway.common.result.ResultCode;
import com.zimo.gateway.common.util.IpUtil;
import com.zimo.gateway.user.entity.UserEntity;
import com.zimo.gateway.user.model.LoginRequest;
import com.zimo.gateway.user.model.LoginVO;
import com.zimo.gateway.user.model.RegisterRequest;
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

    /**
     * 注册：默认 VIEWER 角色，成功即登录（返回令牌）
     */
    @PostMapping("/register")
    public R<LoginVO> register(@Valid @RequestBody RegisterRequest request, HttpServletRequest httpRequest) {
        return R.ok(userService.register(request, IpUtil.get(httpRequest)));
    }

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
