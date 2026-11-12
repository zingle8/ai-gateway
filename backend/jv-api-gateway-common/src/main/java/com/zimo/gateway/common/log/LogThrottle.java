package com.zimo.gateway.common.log;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 日志限速（v0.2 第 14 项）：高频节点（鉴权拦截、限流拒绝、上报失败）按 key
 * 做每秒采样，超出的日志直接丢弃，防止被刷屏流量写爆磁盘。
 *
 * <p>实现为「每秒一个窗口 + 计数器」，无锁 CAS；key 建议用「业务动作 + 主体」
 * （如 {@code auth.deny:key:ay6zq7yr}），窗口每秒重置。
 */
public final class LogThrottle {

    private record Window(long second, AtomicLong count) {
    }

    private static final ConcurrentHashMap<String, Window> WINDOWS = new ConcurrentHashMap<>();

    private LogThrottle() {
    }

    /** 该 key 本秒是否还允许打印（每次调用消耗 1 个配额） */
    public static boolean allow(String key, long permitsPerSecond) {
        if (permitsPerSecond <= 0) {
            return true;
        }
        long nowSecond = System.currentTimeMillis() / 1000L;
        Window window = WINDOWS.compute(key, (k, old) ->
                old == null || old.second() != nowSecond ? new Window(nowSecond, new AtomicLong(0)) : old);
        return window.count().incrementAndGet() <= permitsPerSecond;
    }

    /** 默认每秒 5 条 */
    public static boolean allow(String key) {
        return allow(key, 5);
    }

    /** 供测试 / 手工清理 */
    public static void clear() {
        WINDOWS.clear();
    }
}
