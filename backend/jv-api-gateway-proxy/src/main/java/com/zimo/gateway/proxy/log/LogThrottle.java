package com.zimo.gateway.proxy.log;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 网关日志限速（v0.2 第 14 项）：鉴权拦截、限流拒绝等高频节点的日志按
 * 「key 每秒 N 条」采样，超出丢弃，避免被刷屏流量写爆磁盘。
 *
 * <p>proxy 不依赖 common 模块（不引入 Servlet 栈），因此本地保留一份轻量实现。
 */
public final class LogThrottle {

    private record Window(long second, AtomicLong count) {
    }

    private static final ConcurrentHashMap<String, Window> WINDOWS = new ConcurrentHashMap<>();

    private LogThrottle() {
    }

    public static boolean allow(String key, long permitsPerSecond) {
        if (permitsPerSecond <= 0) {
            return true;
        }
        long nowSecond = System.currentTimeMillis() / 1000L;
        Window window = WINDOWS.compute(key, (k, old) ->
                old == null || old.second() != nowSecond ? new Window(nowSecond, new AtomicLong(0)) : old);
        return window.count().incrementAndGet() <= permitsPerSecond;
    }

    public static boolean allow(String key) {
        return allow(key, 5);
    }
}
