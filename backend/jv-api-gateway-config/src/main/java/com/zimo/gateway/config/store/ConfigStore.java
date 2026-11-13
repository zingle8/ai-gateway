package com.zimo.gateway.config.store;

/**
 * 配置存储来源（DB / Redis / Nacos）统一抽象。
 *
 * <p>元数据永远在 MySQL 的 tb_sys_config；这里只负责"值"的读写。
 * 远端不可用时抛 {@link ConfigStoreException}，由上层回落快照并标记 STALE。
 */
public interface ConfigStore {

    /** DB / REDIS / NACOS */
    String source();

    /** 人类可读的来源描述，例如 "Redis · 127.0.0.1:6379/0 · gw:config:platform" */
    String label(String sourceRef);

    /**
     * 读取值。
     *
     * @return 远端当前值；null 表示暂无该 key（调用方用快照兜底）
     * @throws ConfigStoreException 远端不可达 / 鉴权失败
     */
    String read(String key, String sourceRef);

    /** 写入值（覆盖） */
    void write(String key, String value, String sourceRef);

    /** 删除值 */
    void delete(String key, String sourceRef);

    /**
     * 整文件读取（v0.2 配置集中：基础设施配置整份展示 / 编辑用）。
     * 不支持整文件的存储返回 null。
     */
    default String readAll(String sourceRef) {
        return null;
    }

    /** 整文件发布（覆盖全文）；不支持的存储抛 ConfigStoreException */
    default void writeAll(String content, String sourceRef) {
        throw new ConfigStoreException("该存储不支持整文件发布");
    }
}
