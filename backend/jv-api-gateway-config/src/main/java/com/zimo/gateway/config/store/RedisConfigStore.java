package com.zimo.gateway.config.store;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Redis 来源：一个 Hash 存多份配置，field = config_key。
 *
 * <p>Hash 键由配置项 source_ref 指定，未指定时用默认 {@code gw:config:platform}。
 * 连接沿用 spring.data.redis 配置（与平台其余模块一致）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisConfigStore implements ConfigStore {

    public static final String SOURCE = "REDIS";
    public static final String DEFAULT_HASH = "gw:config:platform";

    private final StringRedisTemplate redisTemplate;
    private final RedisProperties redisProperties;

    @Override
    public String source() {
        return SOURCE;
    }

    @Override
    public String label(String sourceRef) {
        return "Redis · " + host() + ":" + port() + "/" + database() + " · " + hash(sourceRef);
    }

    @Override
    public String read(String key, String sourceRef) {
        try {
            Object value = redisTemplate.opsForHash().get(hash(sourceRef), key);
            return value == null ? null : String.valueOf(value);
        } catch (Exception e) {
            throw new ConfigStoreException("Redis 读取失败：" + e.getMessage(), e);
        }
    }

    @Override
    public void write(String key, String value, String sourceRef) {
        try {
            redisTemplate.opsForHash().put(hash(sourceRef), key, value);
        } catch (Exception e) {
            throw new ConfigStoreException("Redis 写入失败：" + e.getMessage(), e);
        }
    }

    @Override
    public void delete(String key, String sourceRef) {
        try {
            redisTemplate.opsForHash().delete(hash(sourceRef), key);
        } catch (Exception e) {
            throw new ConfigStoreException("Redis 删除失败：" + e.getMessage(), e);
        }
    }

    private String hash(String sourceRef) {
        return sourceRef == null || sourceRef.isBlank() ? DEFAULT_HASH : sourceRef.trim();
    }

    private String host() {
        return redisProperties.getHost() == null ? "127.0.0.1" : redisProperties.getHost();
    }

    private int port() {
        return redisProperties.getPort();
    }

    private int database() {
        return redisProperties.getDatabase();
    }
}
