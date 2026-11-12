package com.zimo.gateway.proxy.ratelimit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 令牌桶限流执行器。
 *
 * <pre>
 * 桶状态 = { tokens 当前令牌数, ts 上次刷新时间 }
 * 每次请求：tokens += (now - ts) * rate / 1000，上限 burstCapacity
 *           tokens >= requestedTokens → 扣减并放行，否则拒绝
 * </pre>
 *
 * <p>Redis 优先（Lua 脚本保证原子性，多网关实例共享桶）；Redis 不可用时
 * 自动回落本地令牌桶（单实例内存，30 秒后重试 Redis），保证网关不因缓存故障停摆。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TokenBucketLimiter {

    private static final String KEY_PREFIX = "gw:rl:";
    private static final long REDIS_RETRY_MILLIS = 30_000L;
    private static final long MIN_TTL_MILLIS = 60_000L;

    private static final String LUA = """
            local key = KEYS[1]
            local capacity = tonumber(ARGV[1])
            local rate = tonumber(ARGV[2])
            local now = tonumber(ARGV[3])
            local requested = tonumber(ARGV[4])
            local ttl = tonumber(ARGV[5])
            local data = redis.call('HMGET', key, 'tokens', 'ts')
            local tokens = tonumber(data[1])
            local ts = tonumber(data[2])
            if tokens == nil or ts == nil then
              tokens = capacity
              ts = now
            end
            local delta = math.max(0, now - ts) / 1000.0
            tokens = math.min(capacity, tokens + delta * rate)
            local allowed = 0
            if tokens >= requested then
              tokens = tokens - requested
              allowed = 1
            end
            redis.call('HMSET', key, 'tokens', tokens, 'ts', now)
            redis.call('PEXPIRE', key, ttl)
            return {allowed, math.floor(tokens)}
            """;

    private static final DefaultRedisScript<List> SCRIPT = new DefaultRedisScript<>(LUA, List.class);

    private final ObjectProvider<StringRedisTemplate> redisProvider;

    /** 本地兜底桶：key = 规则 + 主体 */
    private final Map<String, LocalBucket> localBuckets = new ConcurrentHashMap<>();

    private final AtomicLong redisDownUntil = new AtomicLong(0);

    /**
     * 尝试获取令牌。
     *
     * @param subject 限流主体（GLOBAL/ROUTE 为 all，API_KEY 为 keyId，IP 为客户端 IP）
     * @return 决策结果（是否放行 + 剩余令牌 + 桶容量）
     */
    public Decision tryAcquire(RateLimitRule rule, String subject) {
        int capacity = positive(rule.getBurstCapacity(), 100);
        int rate = positive(rule.getReplenishRate(), 100);
        int requested = positive(rule.getRequestedTokens(), 1);
        String key = KEY_PREFIX + rule.getId() + ":" + subject;
        long now = System.currentTimeMillis();
        long ttl = Math.max(MIN_TTL_MILLIS, capacity * 2000L / rate);

        StringRedisTemplate redis = redisProvider.getIfAvailable();
        if (redis != null && redisAvailable()) {
            try {
                List<?> result = redis.execute(SCRIPT, List.of(key),
                        String.valueOf(capacity), String.valueOf(rate), String.valueOf(now),
                        String.valueOf(requested), String.valueOf(ttl));
                if (result != null && result.size() >= 2) {
                    boolean allowed = toLong(result.get(0)) == 1L;
                    return new Decision(allowed, toLong(result.get(1)), capacity, rule, subject);
                }
            } catch (Exception e) {
                redisDownUntil.set(now + REDIS_RETRY_MILLIS);
                log.warn("[限流] Redis 令牌桶异常，回落本地计数（30 秒后重试）：{}", e.getMessage());
            }
        }
        return localAcquire(key, capacity, rate, requested, now, rule, subject);
    }

    private Decision localAcquire(String key, int capacity, int rate, int requested,
                                  long now, RateLimitRule rule, String subject) {
        LocalBucket bucket = localBuckets.computeIfAbsent(key, k -> new LocalBucket(capacity, now));
        long remaining;
        synchronized (bucket) {
            double delta = Math.max(0, now - bucket.ts) / 1000.0;
            bucket.tokens = Math.min(capacity, bucket.tokens + delta * rate);
            bucket.ts = now;
            boolean allowed = bucket.tokens >= requested;
            if (allowed) {
                bucket.tokens -= requested;
            }
            remaining = (long) Math.floor(bucket.tokens);
            if (!allowed) {
                return new Decision(false, remaining, capacity, rule, subject);
            }
        }
        return new Decision(true, remaining, capacity, rule, subject);
    }

    private boolean redisAvailable() {
        return System.currentTimeMillis() >= redisDownUntil.get();
    }

    private static int positive(Integer value, int fallback) {
        return value == null || value < 1 ? fallback : value;
    }

    private static long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    /**
     * 限流决策
     *
     * @param allowed   是否放行
     * @param remaining 剩余令牌数
     * @param limit     桶容量（响应头 X-RateLimit-Limit）
     * @param rule      触发的规则
     * @param subject   限流主体
     */
    public record Decision(boolean allowed, long remaining, int limit, RateLimitRule rule, String subject) {
    }

    /** 本地令牌桶 */
    private static final class LocalBucket {

        private double tokens;

        private long ts;

        private LocalBucket(int capacity, long now) {
            this.tokens = capacity;
            this.ts = now;
        }
    }

    /** 便于日志与调试：当前本地桶数量 */
    public int localBucketCount() {
        return localBuckets.size();
    }

    /** 测试辅助：清空本地桶 */
    public void clearLocal() {
        Arrays.stream(localBuckets.keySet().toArray(new String[0])).forEach(localBuckets::remove);
    }
}
