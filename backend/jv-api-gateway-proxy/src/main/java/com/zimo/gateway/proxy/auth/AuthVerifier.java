package com.zimo.gateway.proxy.auth;

import com.zimo.gateway.proxy.mapper.AuthQueryMapper;
import io.jsonwebtoken.Claims;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 双通道鉴权裁决：
 *  1. Authorization: Bearer &lt;jwt&gt;  —— 验签 + 过期 → 比对 tb_sys_user.token_version
 *  2. X-Api-Key: gwak-&lt;keyId&gt;-&lt;secret&gt; —— 定位 Key → SHA-256 比对 → 状态 / 过期检查
 *
 * <p>缓存策略见 {@link AuthCacheStore}：Redis 优先、MySQL 兜底（MySQL 始终保存源数据），
 * 模式由系统设置 platform.auth.cache.mode 控制，30 秒内生效。
 * 缓存意味着：停用 / 轮换 Key、退出登录（token_version 自增）最迟 30 秒生效。
 *
 * <p>异常容错：共享库不可用时放行并告警（保持网关可用性，与路由加载的容错策略一致）；
 * 正常校验不通过一律 401。
 */
@Slf4j
@Component
public class AuthVerifier {

    private static final String KEY_PREFIX = "gwak-";

    private final ProxyJwtParser jwtParser;
    private final AuthQueryMapper authQueryMapper;
    private final AuthCacheStore cacheStore;
    /** Redis 可选：存放会话吊销标记 gw:sess:revoked:{sid} */
    private final ObjectProvider<StringRedisTemplate> redisProvider;

    /** keyId → 上次记录 last_used_at 的时间，60 秒内不重复写库 */
    private final Map<String, Long> lastTouch = new ConcurrentHashMap<>();

    public AuthVerifier(ProxyJwtParser jwtParser, AuthQueryMapper authQueryMapper, AuthCacheStore cacheStore,
                        ObjectProvider<StringRedisTemplate> redisProvider) {
        this.jwtParser = jwtParser;
        this.authQueryMapper = authQueryMapper;
        this.cacheStore = cacheStore;
        this.redisProvider = redisProvider;
    }

    /**
     * 判定结果；userId / keyId 用于通过后向下游透传身份。
     *
     * <p>status 决定拒绝时的 HTTP 状态码：凭证无效 / 缺失 → 401，
     * 凭证有效但越界（IP 不在白名单、Key 未授权该服务或平台）→ 403。
     * scope 仅在 API Key 通道返回，供后续过滤器做作用域与限流校验。
     */
    public record Decision(boolean allow, String message, Long userId, String apiKeyId,
                           int status, ApiKeyScope scope) {

        public static Decision deny(String message) {
            return new Decision(false, message, null, null, 401, null);
        }

        public static Decision forbidden(String message) {
            return new Decision(false, message, null, null, 403, null);
        }

        public static Decision allowUser(Long userId) {
            return new Decision(true, null, userId, null, 200, null);
        }

        public static Decision allowKey(String keyId, ApiKeyScope scope) {
            return new Decision(true, null, null, keyId, 200, scope);
        }
    }

    public Decision verifyBearer(String token) {
        Claims claims = jwtParser.parse(token);
        if (claims == null) {
            return Decision.deny("令牌无效或已过期");
        }
        Object raw = claims.get("userId");
        if (raw == null) {
            return Decision.deny("令牌缺少用户标识");
        }
        Long userId = Long.valueOf(String.valueOf(raw));
        int tokenVersion = Integer.parseInt(String.valueOf(claims.getOrDefault("tv", 0)));

        Map<String, Object> state = cacheStore.userState(userId);
        if (state == null) {
            return Decision.deny("账号不存在");
        }
        if (((Number) state.getOrDefault("status", 1)).intValue() == 0) {
            return Decision.deny("账号已禁用");
        }
        int dbVersion = ((Number) state.getOrDefault("token_version", 0)).intValue();
        if (tokenVersion != dbVersion) {
            return Decision.deny("账号已退出登录，请重新登录");
        }
        // 会话级吊销：单会话 / 按平台 / 按端踢人后，该 sid 的令牌立即失效
        if (sessionRevoked(claims.get("sid"))) {
            return Decision.deny("该会话已被强制下线，请重新登录");
        }
        return Decision.allowUser(userId);
    }

    /**
     * API Key 通道校验：格式 → 状态 → 有效期 → 密钥哈希 → 调用方 IP 白名单。
     * IP 不在白名单时返回 403（凭证本身有效，属于越界访问）。
     */
    public Decision verifyApiKey(String header, String clientIp) {
        if (!header.startsWith(KEY_PREFIX)) {
            return Decision.deny("API Key 格式不正确");
        }
        String rest = header.substring(KEY_PREFIX.length());
        int split = rest.indexOf('-');
        if (split <= 0 || split == rest.length() - 1) {
            return Decision.deny("API Key 格式不正确");
        }
        String keyId = rest.substring(0, split);
        String secret = rest.substring(split + 1);

        Map<String, Object> key = cacheStore.keyMeta(keyId);
        if (key == null) {
            return Decision.deny("无效的 API Key");
        }
        if (((Number) key.getOrDefault("status", 0)).intValue() == 0) {
            return Decision.deny("API Key 已停用");
        }
        Object expireAt = key.get("expire_at");
        if (expireAt instanceof LocalDateTime expire && expire.isBefore(LocalDateTime.now())) {
            return Decision.deny("API Key 已过期");
        }
        if (!sha256Hex(secret).equals(String.valueOf(key.get("secret_hash")))) {
            return Decision.deny("API Key 校验失败");
        }
        String allowedIps = key.get("allowed_ips") == null ? null : String.valueOf(key.get("allowed_ips"));
        if (!IpWhitelist.matches(allowedIps, clientIp)) {
            if (com.zimo.gateway.proxy.log.LogThrottle.allow("auth.ip.deny:" + keyId, 3)) {
                log.info("[鉴权] API Key {} 调用方 IP {} 不在白名单内，拒绝访问", keyId, clientIp);
            }
            return Decision.forbidden("调用方 IP " + clientIp + " 不在该 API Key 的 IP 白名单内");
        }
        touchLastUsed(keyId, clientIp);
        return Decision.allowKey(keyId, new ApiKeyScope(keyId,
                splitCsv(key.get("allowed_services")),
                splitCsv(key.get("allowed_platforms")),
                intValue(key.get("rate_limit_per_minute"))));
    }

    private static java.util.List<String> splitCsv(Object raw) {
        if (raw == null) {
            return java.util.List.of();
        }
        String text = String.valueOf(raw).trim();
        if (text.isEmpty()) {
            return java.util.List.of();
        }
        java.util.List<String> items = new java.util.ArrayList<>();
        for (String part : text.split(",")) {
            String item = part.trim();
            if (!item.isEmpty()) {
                items.add(item);
            }
        }
        return items;
    }

    private static int intValue(Object raw) {
        return raw instanceof Number number ? number.intValue() : 0;
    }

    /** 最近使用时间 / 来源 IP 回写：60 秒内不重复写库，失败不影响放行 */
    private void touchLastUsed(String keyId, String clientIp) {
        long now = System.currentTimeMillis();
        Long last = lastTouch.get(keyId);
        if (last != null && now - last < 60_000L) {
            return;
        }
        lastTouch.put(keyId, now);
        try {
            authQueryMapper.touchLastUsed(keyId, LocalDateTime.now(), clientIp);
        } catch (Exception e) {
            log.debug("[鉴权] 更新 Key 最近使用记录失败：{}", e.getMessage());
        }
    }

    /**
     * 会话吊销校验：读 gw:sess:revoked:{sid}。
     * Redis 不可用时返回 false（放行），与鉴权缓存的容错策略一致。
     */
    private boolean sessionRevoked(Object sid) {
        if (sid == null || String.valueOf(sid).isBlank()) {
            return false;
        }
        StringRedisTemplate redis = redisProvider.getIfAvailable();
        if (redis == null) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(redis.hasKey("gw:sess:revoked:" + sid));
        } catch (Exception e) {
            log.warn("[鉴权] 会话吊销状态查询失败，本次放行：{}", e.getMessage());
            return false;
        }
    }

    static String sha256Hex(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }
}
