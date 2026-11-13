package com.zimo.gateway.proxy.auth;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 网关层 IP 封禁（v0.2 多端与安全增强）：读 user 服务维护的 Redis set {@code gw:ipban}
 * （精确 IP + 部分通配由 user 登录侧兜底），命中即 403 全量拦截。
 *
 * <p>Redis 不可用时 fail-open（放行），可用性优先；登录侧（user 服务）仍会做 DB 兜底校验。
 */
@Slf4j
@Component
public class IpBanStore {

    /** 与 IpBanService.REDIS_SET 同值（proxy 不依赖 user 模块） */
    public static final String REDIS_SET = "gw:ipban";

    private final ObjectProvider<StringRedisTemplate> redisProvider;
    private volatile boolean redisDown = false;

    public IpBanStore(ObjectProvider<StringRedisTemplate> redisProvider) {
        this.redisProvider = redisProvider;
    }

    /** 命中封禁返回 true；Redis 异常返回 false（fail-open） */
    public boolean isBanned(String ip) {
        if (ip == null || ip.isBlank() || redisDown) {
            return false;
        }
        StringRedisTemplate redis = redisProvider.getIfAvailable();
        if (redis == null) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(redis.opsForSet().isMember(REDIS_SET, ip));
        } catch (Exception e) {
            if (!redisDown) {
                redisDown = true;
                log.warn("[IP封禁] Redis 不可用，网关侧停止校验（fail-open）：{}", e.getMessage());
            }
            return false;
        }
    }
}
