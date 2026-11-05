package com.zimo.gateway.user.service;

import com.zimo.gateway.user.mapper.PlatformConfigMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 平台配置读取（10 秒本地缓存）：系统设置页改完最迟 10 秒在本服务生效。
 *
 * <p>配置存于共享库 tb_sys_config，由 jv-api-gateway-config 服务维护；
 * 表尚未创建（config 服务未首次启动）时按默认值放行。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlatformConfigReader {

    private static final long TTL_MILLIS = 10_000L;

    private final PlatformConfigMapper mapper;

    private record Cached(String value, long expireAt) {
    }

    private final Map<String, Cached> cache = new ConcurrentHashMap<>();

    public boolean openAuthEnabled() {
        return !"false".equalsIgnoreCase(get("platform.open.auth.enabled", "true"));
    }

    public String get(String key, String defaultValue) {
        long now = System.currentTimeMillis();
        Cached cached = cache.get(key);
        if (cached != null && cached.expireAt() > now) {
            return cached.value();
        }
        String value;
        try {
            value = mapper.selectValue(key);
        } catch (Exception e) {
            log.debug("读取平台配置失败，使用默认值：{}={}，原因：{}", key, defaultValue, e.getMessage());
            value = null;
        }
        String resolved = value == null ? defaultValue : value;
        cache.put(key, new Cached(resolved, now + TTL_MILLIS));
        return resolved;
    }
}
