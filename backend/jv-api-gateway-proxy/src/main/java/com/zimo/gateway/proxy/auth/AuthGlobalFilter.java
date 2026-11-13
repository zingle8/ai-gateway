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
 * 通过 → 注入 X-Auth-UserEntity-Id / X-Auth-Key-Id 供下游服务识别调用方
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

    /** 与 GatewayClientConstant.HEADER_CLIENT_TOKEN 同值（proxy 不依赖 client-common，保持字符串常量同步） */
    private static final String HEADER_CLIENT_TOKEN = "X-Gateway-Client-Token";

    private final GatewayAuthProperties properties;
    private final AuthVerifier verifier;
    private final AuthExemptRepository exemptRepository;
    private final OpenApiKeyPolicy openApiKeyPolicy;
    private final ReportAclService reportAclService;
    private final IpBanStore ipBanStore;

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
        // IP 封禁（v0.2 增强）：网关入口全量拦截，Redis set gw:ipban 命中即 403（fail-open）
        String clientIp = clientIp(exchange);
        if (ipBanStore.isBanned(clientIp)) {
            if (com.zimo.gateway.proxy.log.LogThrottle.allow("ip.ban:" + clientIp, 5)) {
                log.warn("[IP封禁] 拦截 {} {}（来源 {}）", exchange.getRequest().getMethod(), path, clientIp);
            }
            return deny(exchange, 403, "该 IP 已被风控封禁，如有疑问请联系管理员");
        }
        /*
         * 开放接口开启「调用方约束」后，即使命中白名单也必须校验 API Key（v0.2 第 6 项）。
         * 例外：平台内部服务（SDK 的 GatewayAuthFilter）用共享密钥头调用 /open/auth/token/verify 验签，
         * 它们没有 API Key；携带正确 X-Gateway-Client-Token 的请求按内部调用放行。
         */
        boolean internalCall = clientTokenConfigured() && tokenMatches(exchange.getRequest().getHeaders());
        boolean openGuarded = path.startsWith("/api/open/") && openApiKeyPolicy.required() && !internalCall;
        if (!openGuarded && ("OPTIONS".equalsIgnoreCase(exchange.getRequest().getMethod().name())
                || properties.getSkipPaths().stream().anyMatch(p -> PATH_MATCHER.match(p, path))
                // 控制台「鉴权豁免」配置的追加放行（5 秒生效，yml 白名单仍然有效）
                || exemptRepository.isExempt(path, exchange.getRequest().getMethod().name(), "LOGIN"))) {
            // 上报通道虽在白名单，但配置了共享密钥时必须校验头，防止内网伪造注册与上报
            if (clientTokenConfigured() && isReportPath(path)
                    && !tokenMatches(exchange.getRequest().getHeaders())) {
                log.warn("[鉴权] 上报通道共享密钥校验失败：{}", path);
                return deny(exchange, 401, "上报通道共享密钥无效");
            }
            // 上报通道黑白名单（IP / 服务名，tb_sys_config platform.report.acl，10 秒生效）
            if (isReportPath(path)) {
                String aclDeny = reportAclService.checkReport(clientIp(exchange),
                        exchange.getRequest().getHeaders().getFirst(ReportAclService.HEADER_APP_ID));
                if (aclDeny != null) {
                    if (com.zimo.gateway.proxy.log.LogThrottle.allow("report.acl:" + aclDeny, 5)) {
                        log.warn("[上报ACL] 拦截 {} {}：{}", exchange.getRequest().getMethod(), path, aclDeny);
                    }
                    return deny(exchange, 403, aclDeny);
                }
            }
            return chain.filter(exchange);
        }

        // 校验涉及阻塞 JDBC（缓存未命中时），移出 Netty 事件线程
        return Mono.fromCallable(() -> doVerify(exchange, openGuarded))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(decision -> {
                    if (decision.allow()) {
                        ServerWebExchange mutated = injectIdentity(exchange, decision);
                        return chain.filter(mutated);
                    }
                    // 拦截日志按路径限速，避免被撞库 / 扫描流量刷屏（v0.2 第 14 项）
                    if (com.zimo.gateway.proxy.log.LogThrottle.allow("auth.deny:" + path, 5)) {
                        log.info("[鉴权] 拦截 {} {}（traceId={}）：{}", exchange.getRequest().getMethod(), path,
                                exchange.getAttributes().get(com.zimo.gateway.proxy.log.TraceIdWebFilter.ATTR),
                                decision.message());
                    }
                    return deny(exchange, decision.status(), decision.message());
                });
    }

    private boolean clientTokenConfigured() {
        String t = properties.getClientToken();
        return t != null && !t.isBlank();
    }

    private boolean isReportPath(String path) {
        return properties.getClientTokenPaths().stream().anyMatch(p -> PATH_MATCHER.match(p, path));
    }

    private boolean tokenMatches(HttpHeaders headers) {
        return properties.getClientToken().equals(headers.getFirst(HEADER_CLIENT_TOKEN));
    }

    private AuthVerifier.Decision doVerify(ServerWebExchange exchange, boolean apiKeyOnly) {
        HttpHeaders headers = exchange.getRequest().getHeaders();
        String apiKey = headers.getFirst("X-Api-Key");
        if (apiKey != null && !apiKey.isBlank()) {
            return verifier.verifyApiKey(apiKey.trim(), clientIp(exchange));
        }
        if (apiKeyOnly) {
            return AuthVerifier.Decision.deny("该开放接口已开启调用方约束，请携带 X-Api-Key（不接受用户令牌）");
        }
        String authorization = headers.getFirst(HttpHeaders.AUTHORIZATION);
        if (authorization != null && authorization.startsWith("Bearer ")) {
            return verifier.verifyBearer(authorization.substring("Bearer ".length()).trim());
        }
        return AuthVerifier.Decision.deny("未提供访问凭证");
    }

    /** 调用方 IP：优先 X-Forwarded-For 首个地址（经过前置代理时），否则取 TCP 远端地址 */
    private String clientIp(ServerWebExchange exchange) {
        String forwarded = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        var remote = exchange.getRequest().getRemoteAddress();
        return remote == null || remote.getAddress() == null
                ? "unknown" : remote.getAddress().getHostAddress();
    }

    /**
     * 身份透传：下游服务可从 X-Auth-UserEntity-Id / X-Auth-Key-Id 识别调用方；
     * API Key 的作用域放进 exchange 属性，供后续作用域 / 限流过滤器复用（不再重复查库）。
     */
    private ServerWebExchange injectIdentity(ServerWebExchange exchange, AuthVerifier.Decision decision) {
        var builder = exchange.getRequest().mutate();
        if (decision.userId() != null) {
            builder.header("X-Auth-UserEntity-Id", String.valueOf(decision.userId()));
        }
        if (decision.apiKeyId() != null) {
            builder.header("X-Auth-Key-Id", decision.apiKeyId());
        }
        if (decision.scope() != null) {
            exchange.getAttributes().put(ApiKeyScope.ATTR, decision.scope());
        }
        return exchange.mutate().request(builder.build()).build();
    }

    private Mono<Void> deny(ServerWebExchange exchange, int status, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.valueOf(status));
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"code\":" + status + ",\"message\":\"" + message + "\",\"data\":null}";
        DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }
}
