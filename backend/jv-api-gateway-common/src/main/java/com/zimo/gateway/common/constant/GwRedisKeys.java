package com.zimo.gateway.common.constant;

/**
 * 平台级 Redis Key 约定。
 *
 * 跨服务共享 / 运维检索的 Redis Key 前缀统一在此登记，避免散落各处的魔法字符串。
 */
public final class GwRedisKeys {

    /** 平台配置中心 Hash（系统设置值托管），field 为配置键 */
    public static final String CONFIG_PLATFORM_HASH = "gw:config:platform";

    /** 调度中心分布式锁 Key 前缀（如 scheduler:lock:job:data-clean） */
    public static final String SCHEDULER_LOCK_PREFIX = "scheduler:lock:";

    /** 调度中心配置缓存 Key 前缀（如 scheduler:config:clean-run-time） */
    public static final String SCHEDULER_CONFIG_PREFIX = "scheduler:config:";

    private GwRedisKeys() {
        throw new UnsupportedOperationException("常量类禁止实例化");
    }
}
