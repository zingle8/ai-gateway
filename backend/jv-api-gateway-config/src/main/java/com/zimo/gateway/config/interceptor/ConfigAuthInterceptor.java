package com.zimo.gateway.config.interceptor;

import com.zimo.gateway.common.context.UserContext;
import com.zimo.gateway.common.exception.BizException;
import com.zimo.gateway.common.result.ResultCode;
import com.zimo.gateway.common.util.JwtUtil;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 配置接口登录校验。
 *
 * <p>只做「验签 + 过期」：令牌版本与账号状态由网关（proxy AuthGlobalFilter）在
 * 入口处统一裁决，这里不重复回源查库；直连 28085 的内部调用由网关前置保证。
 */
@Component
@RequiredArgsConstructor
public class ConfigAuthInterceptor implements HandlerInterceptor {

    private static final String BEARER = "Bearer ";

    private final JwtUtil jwtUtil;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER)) {
            throw new BizException(ResultCode.UNAUTHORIZED.getCode(), "未登录或登录已过期");
        }
        Claims claims = jwtUtil.parseToken(header.substring(BEARER.length()));
        if (claims == null) {
            throw new BizException(ResultCode.UNAUTHORIZED.getCode(), "登录已过期，请重新登录");
        }
        Long userId = claims.get("userId") == null ? null : Long.valueOf(String.valueOf(claims.get("userId")));
        UserContext.set(userId, claims.getSubject());
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        UserContext.clear();
    }
}
