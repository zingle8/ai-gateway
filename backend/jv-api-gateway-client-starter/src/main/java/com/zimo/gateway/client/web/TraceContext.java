package com.zimo.gateway.client.web;

/**
 * 请求处理线程上的链路上文（ThreadLocal）。
 *
 * <p>{@link GatewayTraceFilter} 进入时写入「本服务 SERVER span」，
 * 服务向外发起 HTTP 调用时由 {@code TracePropagationInterceptor} 读取，
 * 据此生成 CLIENT span 并向下游注入 traceparent。
 */
public final class TraceContext {

    /** 当前链路上文：traceId + 本服务 SERVER spanId */
    public record Ctx(String traceId, String spanId) {
    }

    private static final ThreadLocal<Ctx> HOLDER = new ThreadLocal<>();

    private TraceContext() {
    }

    public static void set(String traceId, String spanId) {
        HOLDER.set(new Ctx(traceId, spanId));
    }

    public static Ctx get() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }
}
