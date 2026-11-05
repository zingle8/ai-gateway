package com.zimo.gateway.config.store;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 按 source 路由到具体存储实现。
 */
@Component
@RequiredArgsConstructor
public class ConfigStoreRegistry {

    private final DbConfigStore dbStore;
    private final RedisConfigStore redisStore;
    private final NacosConfigStore nacosStore;

    public ConfigStore of(String source) {
        String s = source == null ? "DB" : source.trim().toUpperCase();
        return switch (s) {
            case "REDIS" -> redisStore;
            case "NACOS" -> nacosStore;
            default -> dbStore;
        };
    }

    /** 规范化来源，非法值回落 DB */
    public static String normalize(String source) {
        if (source == null) {
            return "DB";
        }
        String s = source.trim().toUpperCase();
        return switch (s) {
            case "REDIS", "NACOS", "DB" -> s;
            default -> "DB";
        };
    }
}
