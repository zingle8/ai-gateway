package com.zimo.gateway.config.store;

/**
 * 配置存储不可用（连接失败 / 鉴权失败 / 超时）。
 * 上层捕获后回落 MySQL 快照，并把配置项标记为 STALE。
 */
public class ConfigStoreException extends RuntimeException {

    public ConfigStoreException(String message) {
        super(message);
    }

    public ConfigStoreException(String message, Throwable cause) {
        super(message, cause);
    }
}
