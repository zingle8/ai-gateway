package com.zimo.gateway.proxy.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

/**
 * 网关侧 JWT 验签（只解析不签发）。
 *
 * <p>签名密钥与 jv-api-gateway-user 完全一致（gateway.jwt.secret）：
 * user 负责签发，网关负责校验。proxy 不能依赖 jv-api-gateway-common
 * （其中 starter-web 会把 Gateway 变成 Servlet 应用），所以这里自带精简实现。
 */
@Component
@ConditionalOnProperty(prefix = "gateway.jwt", name = "secret")
public class ProxyJwtParser {

    @Value("${gateway.jwt.secret}")
    private String secret;

    private SecretKey key() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /** 解析并验签，失败（签名不符 / 已过期 / 格式错误）返回 null */
    public Claims parse(String token) {
        try {
            return Jwts.parser().verifyWith(key()).build()
                    .parseSignedClaims(token).getPayload();
        } catch (Exception e) {
            return null;
        }
    }
}
