package com.zimo.gateway.proxy.log;

import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * 网关链路标识注入（v0.2 第 14 项）：WebFlux 版本。
 *
 * <p>上游带 {@code X-Trace-Id} 时沿用，否则生成 32 位十六进制并回写响应头；
 * 同时写入 MDC 供日志格式 {@code %X{traceId}} 使用，请求结束清理。
 * 网关是响应式栈，MDC 只在当前事件线程内有效，属于「尽力而为」的日志串联，
 * 跨线程的完整链路仍以 tb_gw_trace_log 为准。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdWebFilter implements WebFilter {

    public static final String HEADER = "X-Trace-Id";
    public static final String MDC_KEY = "traceId";
    /** exchange 属性名：响应式线程切换后 MDC 会丢，日志需要显式取值 */
    public static final String ATTR = "gw.traceId";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String traceId = exchange.getRequest().getHeaders().getFirst(HEADER);
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString().replace("-", "");
        }
        String finalTraceId = traceId;
        MDC.put(MDC_KEY, finalTraceId);
        exchange.getAttributes().put(ATTR, finalTraceId);
        exchange.getResponse().getHeaders().set(HEADER, finalTraceId);
        return chain.filter(exchange).doFinally(signal -> MDC.remove(MDC_KEY));
    }
}
