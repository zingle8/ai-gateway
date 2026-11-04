package com.zimo.gateway.user.interceptor;

import com.zimo.gateway.common.context.UserContext;
import com.zimo.gateway.common.exception.BizException;
import com.zimo.gateway.common.result.ResultCode;
import com.zimo.gateway.common.util.JwtUtil;
import com.zimo.gateway.user.entity.User;
import com.zimo.gateway.user.service.UserService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Objects;

/**
 * 登录拦截器：校验 Authorization: Bearer &lt;token&gt;
 *
 * 校验链路：签名 & 过期时间 → 账号是否存在/是否被禁用 → 令牌版本是否仍有效（退出登录即失效）
 * 任一步失败抛出 401 业务异常，由 GlobalExceptionHandler 统一返回。
 */
@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    private static final String BEARER = "Bearer ";

    private final JwtUtil jwtUtil;
    private final UserService userService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER)) {
            throw new BizException(ResultCode.UNAUTHORIZED.getCode(), "未登录或登录已过期");
        }

        String token = header.substring(BEARER.length());
        Claims claims = jwtUtil.parseToken(token);
        if (claims == null) {
            throw new BizException(ResultCode.UNAUTHORIZED.getCode(), "登录已过期，请重新登录");
        }

        Long userId = claims.get("userId") == null ? null : Long.valueOf(String.valueOf(claims.get("userId")));
        User user = userId == null ? null : userService.getById(userId);
        if (user == null) {
            throw new BizException(ResultCode.UNAUTHORIZED.getCode(), "账号不存在");
        }
        if (Objects.equals(user.getStatus(), 0)) {
            throw new BizException(ResultCode.FORBIDDEN.getCode(), "账号已禁用，请联系管理员");
        }
        if (!Objects.equals(jwtUtil.getTokenVersion(token), nullToZero(user.getTokenVersion()))) {
            throw new BizException(ResultCode.UNAUTHORIZED.getCode(), "账号已退出登录，请重新登录");
        }

        UserContext.set(userId, claims.getSubject());
        return true;
    }

    private static int nullToZero(Integer value) {
        return value == null ? 0 : value;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        UserContext.clear();
    }
}
