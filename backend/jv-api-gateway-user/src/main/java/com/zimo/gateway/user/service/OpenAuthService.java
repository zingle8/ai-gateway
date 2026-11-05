package com.zimo.gateway.user.service;

import com.zimo.gateway.common.context.UserContext;
import com.zimo.gateway.common.exception.BizException;
import com.zimo.gateway.common.result.ResultCode;
import com.zimo.gateway.common.util.JwtUtil;
import com.zimo.gateway.user.entity.User;
import com.zimo.gateway.user.model.LoginRequest;
import com.zimo.gateway.user.model.LoginVO;
import com.zimo.gateway.user.model.OpenVerifyVO;
import com.zimo.gateway.user.model.RegisterRequest;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * 开放认证：注册 / 登录 / 令牌校验，供第三方平台对接。
 *
 * <p>总开关由系统设置 platform.open.auth.enabled 控制（10 秒内生效）。
 * 与平台自身登录共用同一套账号体系与 JWT 密钥：第三方拿到的 token
 * 与平台 token 等价，可在网关继续使用。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OpenAuthService {

    private final UserService userService;
    private final PermService permService;
    private final PlatformConfigReader configReader;
    private final JwtUtil jwtUtil;

    public LoginVO register(RegisterRequest request, String ip) {
        requireEnabled();
        return userService.register(request, ip);
    }

    public LoginVO login(LoginRequest request, String ip) {
        requireEnabled();
        return userService.login(request.getUsername(), request.getPassword(), ip);
    }

    /**
     * 令牌校验：签名 / 过期 → 账号存在且未禁用 → token_version 未失效。
     * 返回 valid=false 而不是抛 401，方便第三方按业务自行处理。
     */
    public OpenVerifyVO verify(String token) {
        requireEnabled();
        if (token == null || token.isBlank()) {
            return OpenVerifyVO.invalid("token 为空");
        }
        Claims claims = jwtUtil.parseToken(token);
        if (claims == null) {
            return OpenVerifyVO.invalid("令牌无效或已过期");
        }
        Long userId = claims.get("userId") == null ? null : Long.valueOf(String.valueOf(claims.get("userId")));
        User user = userId == null ? null : userService.getById(userId);
        if (user == null) {
            return OpenVerifyVO.invalid("账号不存在");
        }
        if (Objects.equals(user.getStatus(), 0)) {
            return OpenVerifyVO.invalid("账号已禁用");
        }
        Integer tokenVersion = jwtUtil.getTokenVersion(token);
        int current = user.getTokenVersion() == null ? 0 : user.getTokenVersion();
        if (tokenVersion == null || tokenVersion != current) {
            return OpenVerifyVO.invalid("账号已退出登录，令牌已失效");
        }
        UserContext.clear();
        return OpenVerifyVO.valid(user, claims.getExpiration(), permService.permsOfUser(userId));
    }

    private void requireEnabled() {
        if (!configReader.openAuthEnabled()) {
            throw new BizException(ResultCode.FORBIDDEN.getCode(), "开放认证接口已关闭（系统设置 → platform.open.auth.enabled）");
        }
    }
}
