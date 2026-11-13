package com.zimo.gateway.user.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zimo.gateway.common.exception.BizException;
import com.zimo.gateway.common.result.ResultCode;
import com.zimo.gateway.common.util.JwtUtil;
import com.zimo.gateway.user.entity.OidcClientEntity;
import com.zimo.gateway.user.entity.SsoLogEntity;
import com.zimo.gateway.user.entity.UserEntity;
import com.zimo.gateway.user.mapper.OidcClientMapper;
import com.zimo.gateway.user.mapper.SsoLogMapper;
import com.zimo.gateway.user.mapper.UserMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 单点登录 SSO（OIDC Provider，v0.2 第 15 项）。
 *
 * <pre>
 * 流程（授权码模式）：
 *   ① SP 携带 client_id / redirect_uri 跳转 GET /oauth/authorize；
 *   ② 浏览器无 IdP 会话（gw_token Cookie）→ 302 到控制台登录页并带回跳参数；
 *      有会话 → 校验接入方与回调地址，签发一次性授权码（Redis，5 分钟），
 *      302 回 redirect_uri?code=xxx&state=xxx（未授权平台 302 带 error=access_denied）；
 *   ③ SP 后端 POST /oauth/token：code + client_secret（SHA-256 比对）→
 *      校验账号-平台授权关系（第 7 项）→ 返回 id_token / access_token（HS256 JWT，复用平台密钥）；
 *   ④ SP 携 access_token 调 GET /oauth/userinfo 实时换取身份（token_version 失配即 401，
 *      即「IdP 登出 → 各平台本地会话随之失效」的机制）；
 *   ⑤ GET /.well-known/openid-configuration 元数据发现。
 * </pre>
 *
 * <p>边界：签名沿用平台 HS256 对称密钥（不引入 RS256 密钥对，jwks 返回空键集，
 * SP 侧验签用共享 secret 或直接走 userinfo 实时校验）；授权码一次性、5 分钟有效、
 * 绑定 client_id + redirect_uri；client_secret 库内只存 SHA-256，原文仅签发 / 轮换时返回一次。
 */
@Slf4j
@Service
public class SsoService {

    private static final String CODE_KEY = "gw:sso:code:";
    private static final Duration CODE_TTL = Duration.ofMinutes(5);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final HexFormat HEX = HexFormat.of();

    private final OidcClientMapper clientMapper;
    private final SsoLogMapper ssoLogMapper;
    private final UserMapper userMapper;
    private final OpenPlatformService openPlatformService;
    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper;

    @Autowired(required = false)
    private StringRedisTemplate redisTemplate;

    /** IdP 对外元数据地址（指向网关） */
    @Value("${gateway.sso.issuer:http://localhost:28090}")
    private String issuer;

    /** 统一登录页地址（控制台前端入口，浏览器跳转用；生产同域部署时与 issuer 同源） */
    @Value("${gateway.sso.login-url:http://localhost:28080/login}")
    private String loginUrl;

    @Value("${gateway.jwt.secret}")
    private String jwtSecret;

    @Value("${gateway.jwt.expire-hours:8}")
    private long expireHours;

    public SsoService(OidcClientMapper clientMapper, SsoLogMapper ssoLogMapper,
                      UserMapper userMapper, OpenPlatformService openPlatformService,
                      JwtUtil jwtUtil, ObjectMapper objectMapper) {
        this.clientMapper = clientMapper;
        this.ssoLogMapper = ssoLogMapper;
        this.userMapper = userMapper;
        this.openPlatformService = openPlatformService;
        this.jwtUtil = jwtUtil;
        this.objectMapper = objectMapper;
    }

    // ============================================================ 接入方登记

    public List<Map<String, Object>> listClients() {
        return clientMapper.selectAll().stream().map(c -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", c.getId());
            row.put("clientId", c.getClientId());
            row.put("clientName", c.getClientName());
            row.put("appId", c.getAppId());
            row.put("redirectUri", c.getRedirectUri());
            row.put("scopes", c.getScopes());
            row.put("status", c.getStatus());
            row.put("createTime", c.getCreateTime());
            return row;
        }).toList();
    }

    /** 签发接入方：client_id / client_secret 原文只在本次响应中返回一次 */
    public Map<String, Object> createClient(String clientName, String appId, String redirectUri) {
        String clientId = "gwsp-" + randomToken(8);
        String secret = "sso-" + randomToken(32);
        OidcClientEntity client = new OidcClientEntity();
        client.setClientId(clientId);
        client.setClientSecret(sha256(secret));
        client.setClientName(clientName);
        client.setAppId(appId == null || appId.isBlank() ? null : appId.trim());
        client.setRedirectUri(redirectUri.trim());
        client.setScopes("openid profile");
        client.setStatus(1);
        client.setCreateTime(LocalDateTime.now());
        clientMapper.insert(client);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", client.getId());
        result.put("clientId", clientId);
        result.put("clientSecret", secret);
        result.put("hint", "client_secret 仅本次返回，请立即保存；库内只存 SHA-256 摘要");
        return result;
    }

    public Map<String, Object> rotateSecret(Long id) {
        OidcClientEntity client = requireClient(id);
        String secret = "sso-" + randomToken(32);
        clientMapper.updateSecret(id, sha256(secret));
        return Map.of("clientId", client.getClientId(), "clientSecret", secret,
                "hint", "旧 secret 立即失效，请同步更新接入方配置");
    }

    public void updateClient(Long id, String clientName, String appId, String redirectUri, Integer status) {
        OidcClientEntity client = requireClient(id);
        OidcClientEntity update = new OidcClientEntity();
        update.setId(client.getId());
        update.setClientName(clientName != null ? clientName : client.getClientName());
        update.setAppId(appId == null || appId.isBlank() ? null : appId.trim());
        update.setRedirectUri(redirectUri != null ? redirectUri.trim() : client.getRedirectUri());
        update.setStatus(status == null ? client.getStatus() : status);
        clientMapper.update(update);
    }

    public void deleteClient(Long id) {
        requireClient(id);
        clientMapper.deleteById(id);
    }

    private OidcClientEntity requireClient(Long id) {
        OidcClientEntity client = id == null ? null : clientMapper.selectById(id);
        if (client == null) {
            throw new BizException(ResultCode.NOT_FOUND.getCode(), "接入方不存在");
        }
        return client;
    }

    // ============================================================ 授权端点

    /**
     * GET /oauth/authorize。返回 302 目标 URL：
     * 已登录 → redirect_uri?code=...&state=...（未授权平台 → error=access_denied）；
     * 未登录 → 登录页（带回跳参数 sso=<本请求完整 URL>）。
     */
    public String authorize(String clientId, String redirectUri, String responseType,
                            String state, String cookieToken, String ip) {
        OidcClientEntity client = clientMapper.selectByClientId(clientId);
        if (client == null) {
            throw new BizException(ResultCode.BAD_REQUEST.getCode(), "未知接入方 client_id");
        }
        if (client.getStatus() == null || client.getStatus() != 1) {
            throw new BizException(ResultCode.FORBIDDEN.getCode(), "接入方已停用");
        }
        if (redirectUri == null || !List.of(client.getRedirectUri().split(","))
                .stream().map(String::trim).toList().contains(redirectUri.trim())) {
            throw new BizException(ResultCode.BAD_REQUEST.getCode(), "回调地址未登记：" + redirectUri);
        }
        if (!"code".equals(responseType)) {
            throw new BizException(ResultCode.BAD_REQUEST.getCode(), "仅支持 response_type=code（授权码模式）");
        }
        String sep = redirectUri.contains("?") ? "&" : "?";

        // 未登录：回控制台登录页并带回跳地址（gw_token Cookie 即 IdP 会话）
        Long userId = sessionUser(cookieToken);
        if (userId == null) {
            logSso(null, null, clientId, client.getAppId(), "AUTHORIZE", "DENIED", ip, "无有效 IdP 会话，跳转登录页");
            String authorizeUrl = issuer + "/api/oauth/authorize?client_id=" + urlEncode(clientId)
                    + "&redirect_uri=" + urlEncode(redirectUri) + "&response_type=code&state="
                    + urlEncode(state == null ? "" : state);
            return loginUrl + "?sso=" + urlEncode(authorizeUrl);
        }
        UserEntity user = userMapper.selectById(userId);
        if (user == null || isDisabled(user)) {
            throw new BizException(ResultCode.UNAUTHORIZED.getCode(), "账号不可用");
        }

        // 认证了 ≠ 能用：换 token 前先查账号-平台授权关系（第 7 项）
        if (client.getAppId() != null && !openPlatformService.isAuthorized(userId, client.getAppId())) {
            logSso(userId, user.getUsername(), clientId, client.getAppId(), "AUTHORIZE", "DENIED", ip,
                    "账号未被授权到平台 " + client.getAppId());
            return redirectUri + sep + "error=access_denied&error_description="
                    + urlEncode("账号未授权到平台 " + client.getAppId())
                    + (state == null || state.isBlank() ? "" : "&state=" + urlEncode(state));
        }

        String code = randomToken(24);
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("userId", userId);
            payload.put("username", user.getUsername());
            payload.put("clientId", clientId);
            payload.put("appId", client.getAppId());
            payload.put("redirectUri", redirectUri.trim());
            StringRedisTemplate redis = redisTemplate;
            if (redis == null) {
                throw new BizException(ResultCode.ERROR.getCode(), "Redis 不可用，无法签发授权码");
            }
            redis.opsForValue().set(CODE_KEY + code, objectMapper.writeValueAsString(payload), CODE_TTL);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(ResultCode.ERROR.getCode(), "授权码写入失败：" + e.getMessage());
        }
        logSso(userId, user.getUsername(), clientId, client.getAppId(), "AUTHORIZE", "SUCCESS", ip, "签发授权码");
        return redirectUri + sep + "code=" + code
                + (state == null || state.isBlank() ? "" : "&state=" + urlEncode(state));
    }

    // ============================================================ token 端点

    public Map<String, Object> token(String clientId, String clientSecret, String code,
                                     String redirectUri, String grantType, String ip) {
        if (!"authorization_code".equals(grantType)) {
            throw new BizException(ResultCode.BAD_REQUEST.getCode(), "仅支持 grant_type=authorization_code");
        }
        OidcClientEntity client = clientMapper.selectByClientId(clientId);
        if (client == null || client.getStatus() == null || client.getStatus() != 1) {
            throw new BizException(ResultCode.UNAUTHORIZED.getCode(), "接入方无效或已停用");
        }
        if (clientSecret == null || !sha256(clientSecret).equals(client.getClientSecret())) {
            logSso(null, null, clientId, client.getAppId(), "TOKEN", "DENIED", ip, "client_secret 校验失败");
            throw new BizException(ResultCode.UNAUTHORIZED.getCode(), "client_secret 校验失败");
        }
        if (redisTemplate == null) {
            throw new BizException(ResultCode.ERROR.getCode(), "Redis 不可用");
        }
        String raw = redisTemplate.opsForValue().get(CODE_KEY + code);
        if (raw == null) {
            throw new BizException(ResultCode.BAD_REQUEST.getCode(), "授权码无效或已过期（一次性，5 分钟有效）");
        }
        redisTemplate.delete(CODE_KEY + code);   // 一次性：取走即删
        Map<String, Object> payload;
        try {
            payload = objectMapper.readValue(raw, Map.class);
        } catch (Exception e) {
            throw new BizException(ResultCode.ERROR.getCode(), "授权码解析失败");
        }
        if (!clientId.equals(String.valueOf(payload.get("clientId")))) {
            throw new BizException(ResultCode.BAD_REQUEST.getCode(), "授权码与 client_id 不匹配");
        }
        if (redirectUri == null || !redirectUri.trim().equals(String.valueOf(payload.get("redirectUri")))) {
            throw new BizException(ResultCode.BAD_REQUEST.getCode(), "redirect_uri 与授权请求不一致");
        }
        Long userId = Long.valueOf(String.valueOf(payload.get("userId")));
        String username = String.valueOf(payload.get("username"));
        UserEntity user = userMapper.selectById(userId);
        if (user == null || isDisabled(user)) {
            throw new BizException(ResultCode.UNAUTHORIZED.getCode(), "账号不可用");
        }
        // 二次校验授权关系（授权码签发后授权可能被回收）
        if (client.getAppId() != null && !openPlatformService.isAuthorized(userId, client.getAppId())) {
            logSso(userId, username, clientId, client.getAppId(), "TOKEN", "DENIED", ip,
                    "账号未被授权到平台 " + client.getAppId());
            throw new BizException(ResultCode.FORBIDDEN.getCode(), "账号未授权到平台 " + client.getAppId());
        }

        long ttl = expireHours * 3600;
        String idToken = claimsToken(Map.of(
                "iss", issuer,
                "sub", username,
                "aud", clientId,
                "userId", userId,
                "tv", user.getTokenVersion() == null ? 0 : user.getTokenVersion(),
                "typ", "id"), ttl);
        String accessToken = claimsToken(Map.of(
                "iss", issuer,
                "sub", username,
                "userId", userId,
                "tv", user.getTokenVersion() == null ? 0 : user.getTokenVersion(),
                "typ", "access"), ttl);

        logSso(userId, username, clientId, client.getAppId(), "TOKEN", "SUCCESS", ip, "签发 id_token / access_token");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("access_token", accessToken);
        result.put("id_token", idToken);
        result.put("token_type", "Bearer");
        result.put("expires_in", ttl);
        result.put("scope", client.getScopes());
        return result;
    }

    // ============================================================ userinfo / 元数据

    /** 实时校验 access_token：token_version 失配（IdP 登出 / 被踢）即 401 */
    public Map<String, Object> userinfo(String authorizationHeader, String ip) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            throw new BizException(ResultCode.UNAUTHORIZED.getCode(), "缺少 Bearer access_token");
        }
        var claims = jwtUtil.parseToken(authorizationHeader.substring(7).trim());
        if (claims == null || !"access".equals(String.valueOf(claims.get("typ")))) {
            throw new BizException(ResultCode.UNAUTHORIZED.getCode(), "access_token 无效");
        }
        Long userId = Long.valueOf(String.valueOf(claims.get("userId")));
        UserEntity user = userMapper.selectById(userId);
        if (user == null || isDisabled(user)
                || !java.util.Objects.equals(claims.get("tv", Integer.class),
                        user.getTokenVersion() == null ? 0 : user.getTokenVersion())) {
            throw new BizException(ResultCode.UNAUTHORIZED.getCode(), "会话已失效（IdP 侧登出或版本变更）");
        }
        logSso(userId, user.getUsername(), null, null, "USERINFO", "SUCCESS", ip, "换取身份信息");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sub", user.getUsername());
        result.put("username", user.getUsername());
        result.put("userId", user.getId());
        result.put("apps", openPlatformService.appIdsOfUser(userId));
        return result;
    }

    public Map<String, Object> discovery() {
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("issuer", issuer);
        doc.put("authorization_endpoint", issuer + "/api/oauth/authorize");
        doc.put("token_endpoint", issuer + "/api/oauth/token");
        doc.put("userinfo_endpoint", issuer + "/api/oauth/userinfo");
        doc.put("jwks_uri", issuer + "/api/oauth/jwks");
        doc.put("response_types_supported", List.of("code"));
        doc.put("grant_types_supported", List.of("authorization_code"));
        doc.put("subject_types_supported", List.of("public"));
        doc.put("id_token_signing_alg_values_supported", List.of("HS256"));
        doc.put("scopes_supported", List.of("openid", "profile"));
        doc.put("token_endpoint_auth_methods_supported", List.of("client_secret_post"));
        doc.put("claims_supported", List.of("sub", "username", "userId", "apps"));
        return doc;
    }

    /**
     * jwks：平台沿用 HS256 对称密钥，对称密钥不发布（发布即任何人可伪造令牌），
     * 返回空键集并在 discovery 标注 HS256；SP 侧用共享 secret 验签或直接走 userinfo。
     */
    public Map<String, Object> jwks() {
        return Map.of("keys", List.of());
    }

    // ============================================================ 审计

    public Map<String, Object> pageLogs(String clientId, String action, int page, int size) {
        long offset = (long) (Math.max(page, 1) - 1) * Math.max(size, 1);
        List<SsoLogEntity> records = ssoLogMapper.selectPage(clientId, action, offset, Math.min(Math.max(size, 1), 200));
        long total = ssoLogMapper.countPage(clientId, action);
        return Map.of("records", records, "total", total, "page", page, "size", size);
    }

    private void logSso(Long userId, String username, String clientId, String appId,
                        String action, String result, String ip, String detail) {
        try {
            SsoLogEntity row = new SsoLogEntity();
            row.setUserId(userId);
            row.setUsername(username);
            row.setClientId(clientId);
            row.setAppId(appId);
            row.setAction(action);
            row.setResult(result);
            row.setIp(ip);
            row.setDetail(detail);
            row.setCreateTime(LocalDateTime.now());
            ssoLogMapper.insert(row);
        } catch (Exception e) {
            log.warn("[SSO] 审计写入失败 action={}：{}", action, e.getMessage());
        }
    }

    // ============================================================ 工具

    /** JSON 串解析（token 端点 JSON body 兜底用） */
    public Map<String, Object> parseJson(String raw) {
        try {
            return objectMapper.readValue(raw, Map.class);
        } catch (Exception e) {
            throw new BizException(ResultCode.BAD_REQUEST.getCode(), "JSON 解析失败：" + e.getMessage());
        }
    }

    /** 从 gw_token Cookie（或 fallback Bearer）解析当前登录用户；登出 / 被踢（tv 失配）视为未登录 */
    private Long sessionUser(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        var claims = jwtUtil.parseToken(token);
        if (claims == null) {
            return null;
        }
        Long userId = claims.get("userId", Long.class);
        if (userId == null) {
            Object v = claims.get("userId");
            userId = v == null ? null : Long.valueOf(String.valueOf(v));
        }
        if (userId == null) {
            return null;
        }
        UserEntity user = userMapper.selectById(userId);
        if (user == null) {
            return null;
        }
        Integer tv = claims.get("tv", Integer.class);
        if (!java.util.Objects.equals(tv == null ? 0 : tv, user.getTokenVersion() == null ? 0 : user.getTokenVersion())) {
            return null;
        }
        return userId;
    }

    /** 签发自定义 claims 的 JWT（id_token / access_token），与平台令牌同一把 HS256 密钥 */
    private String claimsToken(Map<String, Object> claims, long ttlSeconds) {
        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        Date now = new Date();
        var builder = Jwts.builder().issuedAt(now).expiration(new Date(now.getTime() + ttlSeconds * 1000));
        claims.forEach(builder::claim);
        return builder.signWith(key).compact();
    }

    private boolean isDisabled(UserEntity user) {
        return user.getStatus() == null || user.getStatus() == 0;
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HEX.formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

    private String randomToken(int len) {
        String alphabet = "abcdefghijklmnopqrstuvwxyz234567";
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            sb.append(alphabet.charAt(RANDOM.nextInt(alphabet.length())));
        }
        return sb.toString();
    }

    private String urlEncode(String value) {
        try {
            return java.net.URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return value == null ? "" : value;
        }
    }
}
