package com.zimo.gateway.proxy.auth;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.nio.charset.StandardCharsets;

/**
 * 网关统一鉴权过滤器（整个平台的入口门卫）：
 *
 * <pre>
 * 请求 → 白名单（登录 / 注册 / 探活）→ 放行
 *      → OPTIONS 预检 → 放行
 *      → X-Api-Key     → API Key 通道（SHA-256 + 状态 + 有效期）
 *      → Bearer JWT    → 用户令牌通道（验签 + token_version）
 *      → 都没有        → 401
 * 通过 → 注入 X-Auth-User-Id / X-Auth-Key-Id 供下游服务识别调用方
 * </pre>
 *
 * <p>注意：必须排在最前（Order 高于链路采集过滤器），未通过鉴权的请求
 * 不应产生业务流量与追踪记录。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthGlobalFilter implements GlobalFilter, Ordered {

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    private final GatewayAuthProperties properties;
    private final AuthVerifier verifier;

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!properties.isEnabled()) {
            return chain.filter(exchange);
        }

        String path = exchange.getRequest().getPath().value();
        if ("OPTIONS".equalsIgnoreCase(exchange.getRequest().getMethod().name())
                || properties.getSkipPaths().stream().anyMatch(p -> PATH_MATCHER.match(p, path))) {
            return chain.filter(exchange);
        }

        // 校验涉及阻塞 JDBC（缓存未命中时），移出 Netty 事件线程
        return Mono.fromCallable(() -> doVerify(exchange))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(decision -> {
                    if (decision.allow()) {
                        ServerWebExchange mutated = injectIdentity(exchange, decision);
                        return chain.filter(mutated);
                    }
                    log.info("[鉴权] 拦截 {} {}：{}", exchange.getRequest().getMethod(), path, decision.message());
                    return deny(exchange, decision.message());
                });
    }

    private AuthVerifier.Decision doVerify(ServerWebExchange exchange) {
        HttpHeaders headers = exchange.getRequest().getHeaders();
        String apiKey = headers.getFirst("X-Api-Key");
        if (apiKey != null && !apiKey.isBlank()) {
            return verifier.verifyApiKey(apiKey.trim());
        }
        String authorization = headers.getFirst(HttpHeaders.AUTHORIZATION);
        if (authorization != null && authorization.startsWith("Bearer ")) {
            return verifier.verifyBearer(authorization.substring("Bearer ".length()).trim());
        }
        return AuthVerifier.Decision.deny("未提供访问凭证");
    }

    /** 身份透传：下游服务可从 X-Auth-User-Id / X-Auth-Key-Id 识别调用方 */
    private ServerWebExchange injectIdentity(ServerWebExchange exchange, AuthVerifier.Decision decision) {
        var builder = exchange.getRequest().mutate();
        if (decision.userId() != null) {
            builder.header("X-Auth-User-Id", String.valueOf(decision.userId()));
        }
        if (decision.apiKeyId() != null) {
            builder.header("X-Auth-Key-Id", decision.apiKeyId());
        }
        return exchange.mutate().request(builder.build()).build();
    }

    private Mono<Void> deny(ServerWebExchange exchange, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"code\":401,\"message\":\"" + message + "\",\"data\":null}";
        DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }
}
