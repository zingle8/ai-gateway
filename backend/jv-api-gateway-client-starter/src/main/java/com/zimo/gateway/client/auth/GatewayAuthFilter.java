package com.zimo.gateway.client.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zimo.gateway.client.common.constant.GatewayClientConstant;
import com.zimo.gateway.client.properties.GatewayClientProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SDK 入口鉴权过滤器（可选启用：zimo.gateway.client.auth.enabled=true）。
 *
 * <pre>
 * 请求 → 豁免路径（/health/** 等）→ 放行
 *     → 无 Authorization / X-Api-Key → 401
 *     → 验签缓存命中（有效期内同 token）→ 放行
 *     → 回调网关开放验签接口 POST {server-addr}/open/auth/token/verify
 *         · Bearer 用户 JWT 与 X-Api-Key（gwak- 前缀）同语义自动识别
 *         · valid=true → 放行；明确无效 → 401
 *     · 网关不可达 → fail-open=true 放行（可用性优先）/ false 403（安全优先，默认）
 * </pre>
 *
 * <p>与网关侧鉴权的关系：走网关的请求已被 proxy 鉴权过一次，此处是第二道门——
 * 防止绕过网关直连业务系统端口（凭据语义与网关一致，凭缓存控制验签成本）。
 * SDK 自身的数据上报是「出站」调用、不经过本过滤器，因此调用方验签失败与否
 * 不影响日志与 span 上报——网关平台始终能看到这家接入方的真实调用情况。</p>
 */
@Slf4j
public class GatewayAuthFilter extends org.springframework.web.filter.OncePerRequestFilter {

    private static final AntPathMatcher MATCHER = new AntPathMatcher();
    private static final int CACHE_MAX = 2048;

    private final GatewayClientProperties properties;
    private final GatewayClientProperties.Auth auth;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestClient verifyClient;
    private final URI verifyUri;

    /** token → 验签结果缓存（只缓存 valid=true；失败不缓存，避免锁定语义被延长） */
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    public GatewayAuthFilter(GatewayClientProperties properties) {
        this.properties = properties;
        this.auth = properties.getAuth();
        String base = stripTail(properties.getServerAddr());
        this.verifyUri = URI.create(auth.getVerifyUrl() == null || auth.getVerifyUrl().isBlank()
                ? base + GatewayClientConstant.PATH_TOKEN_VERIFY
                : auth.getVerifyUrl());
        // baseUrl 无意义（用完整 URI 直调），仅为复用超时与共享密钥头的构造逻辑
        this.verifyClient = com.zimo.gateway.client.support.RestClientFactory.create(
                base, auth.getTimeoutMs(), properties.getClientToken());
    }

    private static String stripTail(String serverAddr) {
        String s = serverAddr == null ? "" : serverAddr.trim();
        if (s.endsWith("/")) {
            s = s.substring(0, s.length() - 1);
        }
        int idx = s.lastIndexOf("/open/auth");
        return idx > 0 ? s.substring(0, idx) : s;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        // 去掉 context-path 再匹配豁免规则（context-path 因业务系统而异，规则统一按映射路径书写）
        String path = request.getRequestURI().substring(request.getContextPath().length());
        for (String p : auth.getExemptPaths()) {
            if (MATCHER.match(p, path)) {
                chain.doFilter(request, response);
                return;
            }
        }

        String token = extractToken(request);
        if (token == null || token.isBlank()) {
            deny(response, 401, "未提供访问凭证（Authorization: Bearer 或 X-Api-Key）");
            return;
        }

        Boolean valid = cached(token);
        if (valid == null) {
            valid = verifyRemotely(token);
            if (Boolean.TRUE.equals(valid)) {
                cacheIfRoom(token);
            }
        }
        if (Boolean.TRUE.equals(valid)) {
            chain.doFilter(request, response);
            return;
        }
        deny(response, valid == null ? 403 : 401,
                valid == null ? "验签服务不可达" : "凭证无效或已过期");
    }

    private String extractToken(HttpServletRequest request) {
        String authz = request.getHeader("Authorization");
        if (authz != null && authz.startsWith("Bearer ")) {
            return authz.substring(7).trim();
        }
        String apiKey = request.getHeader("X-Api-Key");
        if (apiKey != null && !apiKey.isBlank()) {
            return apiKey.trim();
        }
        return null;
    }

    /** 远程验签：TRUE=有效 / FALSE=明确无效 / null=网关不可达 */
    private Boolean verifyRemotely(String token) {
        try {
            String body = verifyClient.post()
                    .uri(verifyUri)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("token", token))
                    .retrieve()
                    .body(String.class);
            JsonNode node = objectMapper.readTree(body == null ? "{}" : body);
            return node.path("code").asInt() == 200 && node.path("data").path("valid").asBoolean(false);
        } catch (Exception e) {
            log.warn("SDK 入口验签失败（网关不可达）：{}，fail-open={}", e.getMessage(), auth.isFailOpen());
            return auth.isFailOpen() ? Boolean.TRUE : null;
        }
    }

    private Boolean cached(String token) {
        CacheEntry entry = cache.get(token);
        if (entry == null) {
            return null;
        }
        if (System.currentTimeMillis() > entry.expiresAt) {
            cache.remove(token);
            return null;
        }
        return Boolean.TRUE;
    }

    private void cacheIfRoom(String token) {
        if (cache.size() >= CACHE_MAX) {
            cache.clear();
        }
        cache.put(token, new CacheEntry(System.currentTimeMillis() + auth.getCacheSeconds() * 1000L));
    }

    private void deny(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        String safe = message.replace("\"", "'");
        response.getWriter().write(
                "{\"code\":" + status + ",\"message\":\"" + safe + "\",\"data\":null,\"success\":false}");
    }

    private record CacheEntry(long expiresAt) {
    }
}
