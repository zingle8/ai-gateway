package com.zimo.gateway.common.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 工具类：负责令牌签发与校验
 *
 * 位于公共模块，供所有微服务复用；按需装配——
 * 只有配置了 gateway.jwt.secret 的服务（当前为 jv-api-gateway-user）才会激活本组件，
 * 未配置的服务不会因缺少该配置而启动失败。
 */
@Component
@ConditionalOnProperty(prefix = "gateway.jwt", name = "secret")
public class JwtUtil {

    @Value("${gateway.jwt.secret}")
    private String secret;

    @Value("${gateway.jwt.expire-hours:8}")
    private long expireHours;

    private SecretKey key() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 签发令牌
     *
     * @param tokenVersion 令牌版本，取自 tb_sys_user.token_version，
     *                     用于支持「退出登录后立即使已签发令牌失效」
     */
    public String createToken(Long userId, String username, Integer tokenVersion) {
        return createToken(userId, username, tokenVersion, null);
    }

    /**
     * 签发令牌（带会话 ID）
     *
     * @param sid 会话 ID：登录时登记到 Redis，强制下线时写吊销标记，
     *            网关鉴权命中吊销标记即拒绝（支持单会话 / 按平台 / 按端精确踢人）
     */
    public String createToken(Long userId, String username, Integer tokenVersion, String sid) {
        Date now = new Date();
        Date expire = new Date(now.getTime() + expireHours * 3600_000L);
        var builder = Jwts.builder()
                .subject(username)
                .claim("userId", userId)
                .claim("tv", tokenVersion == null ? 0 : tokenVersion);
        if (sid != null && !sid.isBlank()) {
            builder.claim("sid", sid);
        }
        return builder.issuedAt(now)
                .expiration(expire)
                .signWith(key())
                .compact();
    }

    /**
     * 解析令牌，失败返回 null
     */
    public Claims parseToken(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(key())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (Exception e) {
            return null;
        }
    }

    public Long getUserId(String token) {
        Claims claims = parseToken(token);
        if (claims == null) {
            return null;
        }
        Object value = claims.get("userId");
        return value == null ? null : Long.valueOf(String.valueOf(value));
    }

    public String getUsername(String token) {
        Claims claims = parseToken(token);
        return claims == null ? null : claims.getSubject();
    }

    /**
     * 取令牌版本，解析失败返回 null
     */
    public Integer getTokenVersion(String token) {
        Claims claims = parseToken(token);
        if (claims == null) {
            return null;
        }
        Object value = claims.get("tv");
        return value == null ? 0 : Integer.valueOf(String.valueOf(value));
    }
}
