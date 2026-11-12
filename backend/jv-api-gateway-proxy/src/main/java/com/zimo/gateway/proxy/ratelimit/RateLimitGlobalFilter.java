package com.zimo.gateway.proxy.ratelimit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 网关限流过滤器（令牌桶）。
 *
 * <pre>
 * 请求 → OPTIONS / 探活 → 放行
 *      → 加载启用规则（5 秒缓存）
 *      → 逐条匹配（GLOBAL 恒匹配 / ROUTE 按 Ant 路径 / API_KEY 按 keyId / IP 按来源 IP）
 *      → 每条匹配规则独立扣令牌，任一不足 → 429 + Retry-After
 * </pre>
 *
 * <p>排在鉴权之后（{@code HIGHEST_PRECEDENCE + 20}），可复用鉴权注入的调用方信息；
 * 未进入链路的请求不产生追踪记录，避免被限流流量污染调用日志。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitGlobalFilter implements GlobalFilter, Ordered {

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    private static final String API_KEY_HEADER = "X-Api-Key";
    private static final String API_KEY_PREFIX = "gwak-";
    private static final String SUBJECT_ALL = "all";

    private final RateLimitRuleRepository ruleRepository;
    private final TokenBucketLimiter limiter;

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 20;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        if (HttpMethod.OPTIONS.equals(request.getMethod())) {
            return chain.filter(exchange);
        }
        String path = request.getPath().value();
        if (path.startsWith("/health") || path.startsWith("/actuator")) {
            return chain.filter(exchange);
        }
        // 规则加载与 Redis 令牌桶均为阻塞调用，移出 Netty 事件线程
        return Mono.fromCallable(() -> check(request, path))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(outcome -> {
                    if (outcome.allowed()) {
                        writeHeaders(exchange, outcome);
                        return chain.filter(exchange);
                    }
                    log.info("[限流] 拒绝 {} {}：规则「{}」，剩余 {}/{}",
                            request.getMethod(), path, outcome.ruleName(), outcome.remaining(), outcome.limit());
                    return deny(exchange, outcome);
                });
    }

    /** 逐条匹配并扣令牌：任一规则不足即拒绝 */
    private Outcome check(ServerHttpRequest request, String path) {
        List<RateLimitRule> rules = ruleRepository.enabledRules();
        if (rules.isEmpty()) {
            return Outcome.pass();
        }
        String keyId = resolveKeyId(request);
        String ip = resolveIp(request);
        Outcome last = Outcome.pass();
        for (RateLimitRule rule : rules) {
            String subject = subjectOf(rule, path, keyId, ip);
            if (subject == null) {
                continue;
            }
            TokenBucketLimiter.Decision decision = limiter.tryAcquire(rule, subject);
            if (!decision.allowed()) {
                return Outcome.reject(rule.getName(), decision.limit(), decision.remaining());
            }
            last = Outcome.pass(decision.limit(), decision.remaining());
        }
        return last;
    }

    /**
     * 规则 → 限流主体：返回 null 表示该规则不匹配本次请求。
     *
     * <p>GLOBAL/ROUTE 的主体固定为 all（多实例共享同一桶）；API_KEY/IP 的
     * 通配规则按各自主体独立计数。
     */
    private String subjectOf(RateLimitRule rule, String path, String keyId, String ip) {
        String dimension = rule.getDimension() == null ? "" : rule.getDimension().toUpperCase();
        String target = rule.getTarget() == null ? "*" : rule.getTarget().trim();
        return switch (dimension) {
            case "GLOBAL" -> SUBJECT_ALL;
            case "ROUTE" -> PATH_MATCHER.match(target, path) || target.equals(path) ? SUBJECT_ALL : null;
            case "API_KEY" -> keyId != null && ("*".equals(target) || target.equals(keyId))
                    ? "key:" + keyId : null;
            case "IP" -> "*".equals(target) || target.equals(ip) ? "ip:" + ip : null;
            default -> null;
        };
    }

    private String resolveKeyId(ServerHttpRequest request) {
        String raw = request.getHeaders().getFirst(API_KEY_HEADER);
        if (raw == null || !raw.startsWith(API_KEY_PREFIX)) {
            return null;
        }
        String rest = raw.substring(API_KEY_PREFIX.length());
        int split = rest.indexOf('-');
        return split > 0 ? rest.substring(0, split) : null;
    }

    private String resolveIp(ServerHttpRequest request) {
        String forwarded = request.getHeaders().getFirst("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        InetSocketAddress remote = request.getRemoteAddress();
        return remote == null || remote.getAddress() == null
                ? "unknown" : remote.getAddress().getHostAddress();
    }

    private void writeHeaders(ServerWebExchange exchange, Outcome outcome) {
        exchange.getResponse().beforeCommit(() -> {
            HttpHeaders headers = exchange.getResponse().getHeaders();
            headers.set("X-RateLimit-Limit", String.valueOf(outcome.limit()));
            headers.set("X-RateLimit-Remaining", String.valueOf(outcome.remaining()));
            headers.set("X-RateLimit-Reset", "1");
            return Mono.empty();
        });
    }

    private Mono<Void> deny(ServerWebExchange exchange, Outcome outcome) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        response.getHeaders().set("Retry-After", "1");
        response.getHeaders().set("X-RateLimit-Limit", String.valueOf(outcome.limit()));
        response.getHeaders().set("X-RateLimit-Remaining", String.valueOf(outcome.remaining()));
        String body = "{\"code\":429,\"message\":\"请求过于频繁，已触发限流规则「"
                + outcome.ruleName() + "」\",\"data\":null}";
        DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }

    /**
     * 单次请求的限流结果
     *
     * @param allowed   是否放行
     * @param limit     触发/最后一条规则的桶容量
     * @param remaining 剩余令牌
     * @param ruleName  触发/最后一条规则名
     */
    private record Outcome(boolean allowed, int limit, long remaining, String ruleName) {

        private static Outcome pass() {
            return new Outcome(true, 0, 0, null);
        }

        private static Outcome pass(int limit, long remaining) {
            return new Outcome(true, limit, remaining, null);
        }

        private static Outcome reject(String ruleName, int limit, long remaining) {
            return new Outcome(false, limit, remaining, ruleName);
        }
    }
}
