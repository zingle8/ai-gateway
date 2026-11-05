package com.zimo.gateway.proxy.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.zimo.gateway.proxy.mapper.AuthQueryMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 鉴权元数据缓存层：Redis 优先、MySQL 兜底，MySQL 始终保存一份源数据。
 *
 * <pre>
 * 读取顺序（mode = auto）
 *   1. Redis 命中 → 直接返回（Redis 挂了自动跳过）
 *   2. MySQL 回源 → 写入 Redis（TTL 30s）→ 返回
 *   3. Redis 不可用 → Caffeine 本地缓存（TTL 30s）→ MySQL 回源
 *
 * mode = redis：只用 Redis（不可用时仍回落本地，保证网关可用）
 * mode = local：只用本地 Caffeine
 * </pre>
 *
 * <p>mode 来自 tb_sys_config.platform.auth.cache.mode（系统设置页可改），
 * 30 秒刷新一次，改完最迟 30 秒生效。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthCacheStore {

    private static final String KEY_PREFIX = "gw:auth:key:";
    private static final String USER_PREFIX = "gw:auth:user:";
    private static final String MODE_CONFIG_KEY = "platform.auth.cache.mode";
    private static final Duration TTL = Duration.ofSeconds(30);
    private static final long MODE_REFRESH_MILLIS = 30_000L;
    private static final long REDIS_RETRY_MILLIS = 30_000L;

    private final AuthQueryMapper mapper;
    private final GatewayAuthProperties properties;
    private final ObjectMapper objectMapper;
    /** Redis 可选：未引入或未启动时为 null，自动退化为本地缓存 */
    private final ObjectProvider<StringRedisTemplate> redisProvider;

    private final Cache<String, Map<String, Object>> localKeys =
            Caffeine.newBuilder().expireAfterWrite(TTL).maximumSize(2_000).build();

    private final Cache<Long, Map<String, Object>> localUsers =
            Caffeine.newBuilder().expireAfterWrite(TTL).maximumSize(5_000).build();

    private final AtomicLong modeExpireAt = new AtomicLong(0);
    private final AtomicLong redisDownUntil = new AtomicLong(0);
    private volatile String mode = "auto";

    /** 运行时模式（auto 会探测 Redis 可用性） */
    public String resolvedMode() {
        refreshModeIfNeeded();
        if (!"local".equals(mode) && redisAvailable()) {
            return "redis";
        }
        return "local";
    }

    public Map<String, Object> keyMeta(String keyId) {
        return get(KEY_PREFIX + keyId, () -> mapper.selectApiKey(keyId), localKeys, keyId);
    }

    public Map<String, Object> userState(Long userId) {
        return get(USER_PREFIX + userId, () -> mapper.selectUserState(userId), localUsers, userId);
    }

    private <K> Map<String, Object> get(String redisKey,
                                        java.util.function.Supplier<Map<String, Object>> loader,
                                        Cache<K, Map<String, Object>> local, K localKey) {
        if (useRedis()) {
            try {
                String cached = redis().opsForValue().get(redisKey);
                if (cached != null) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> parsed = objectMapper.readValue(cached, Map.class);
                    return parsed;
                }
            } catch (Exception e) {
                markRedisDown("读取", e);
            }
        }
        Map<String, Object> value = local.get(localKey, k -> loadQuietly(loader));
        if (value != null && useRedisSafely()) {
            try {
                redis().opsForValue().set(redisKey, objectMapper.writeValueAsString(value), TTL);
            } catch (Exception e) {
                markRedisDown("回写", e);
            }
        }
        return value;
    }

    private Map<String, Object> loadQuietly(java.util.function.Supplier<Map<String, Object>> loader) {
        try {
            return loader.get();
        } catch (Exception e) {
            log.warn("[鉴权] 回源查询失败：{}", e.getMessage());
            return null;
        }
    }

    private boolean useRedis() {
        return !"local".equals(resolvedMode());
    }

    private boolean useRedisSafely() {
        return useRedis() && redisAvailable();
    }

    private StringRedisTemplate redis() {
        StringRedisTemplate template = redisProvider.getIfAvailable();
        if (template == null) {
            throw new IllegalStateException("未配置 Redis");
        }
        return template;
    }

    /** 30 秒内只探测一次，避免 Redis 故障时每个请求都等连接超时 */
    private boolean redisAvailable() {
        if ("local".equals(mode)) {
            return false;
        }
        long now = System.currentTimeMillis();
        if (redisDownUntil.get() > now) {
            return false;
        }
        StringRedisTemplate template = redisProvider.getIfAvailable();
        if (template == null) {
            redisDownUntil.set(now + REDIS_RETRY_MILLIS);
            return false;
        }
        try {
            template.getConnectionFactory().getConnection().ping();
            return true;
        } catch (Exception e) {
            log.warn("[鉴权] Redis 不可用，回落本地缓存：{}", e.getMessage());
            redisDownUntil.set(now + REDIS_RETRY_MILLIS);
            return false;
        }
    }

    private void markRedisDown(String action, Exception e) {
        log.warn("[鉴权] Redis {} 失败，回落本地缓存：{}", action, e.getMessage());
        redisDownUntil.set(System.currentTimeMillis() + REDIS_RETRY_MILLIS);
    }

    /** 模式来源优先级：tb_sys_config > application.yml；30 秒刷新 */
    private void refreshModeIfNeeded() {
        long now = System.currentTimeMillis();
        if (modeExpireAt.get() > now) {
            return;
        }
        String next = null;
        try {
            next = mapper.selectConfigValue(MODE_CONFIG_KEY);
        } catch (Exception e) {
            log.debug("[鉴权] 读取缓存模式配置失败，使用本地配置：{}", e.getMessage());
        }
        mode = (next == null || next.isBlank()) ? properties.getCacheMode() : next.trim();
        if (!"redis".equals(mode) && !"local".equals(mode) && !"auto".equals(mode)) {
            mode = "auto";
        }
        modeExpireAt.set(now + MODE_REFRESH_MILLIS);
    }
}
