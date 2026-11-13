package com.zimo.gateway.proxy.risk;

import com.zimo.gateway.proxy.auth.ApiKeyScope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * API Key 调用计数（v0.2 第 13 项，风控熔断的数据源）。
 *
 * <p>鉴权通过且走 API Key 通道的请求，按分钟把「调用量 / 错误数」累加进 Redis 哈希：
 * {@code gw:risk:key:{keyId}:{yyyyMMddHHmm}} → {calls, errors}，2 小时过期。
 * user 服务每分钟巡检上一分钟的计数，判定调用量突增 / 错误率异常后自动停用 Key。
 *
 * <p>纯旁路统计：Redis 异常直接跳过，绝不影响转发；只统计 API Key 通道，
 * JWT（控制台用户）流量不进入熔断口径。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KeyCallStatsFilter implements GlobalFilter, Ordered {

    public static final String KEY_PREFIX = "gw:risk:key:";
    private static final DateTimeFormatter MINUTE = DateTimeFormatter.ofPattern("yyyyMMddHHmm");
    private static final Duration TTL = Duration.ofHours(2);

    private final ObjectProvider<StringRedisTemplate> redisProvider;

    @Override
    public int getOrder() {
        /*
         * 必须排在鉴权 / 限流之前（AuthGlobalFilter 为 HIGHEST+10、限流为 HIGHEST+20）：
         * 这两个过滤器拒绝请求时会直接短路，排在它们后面就收不到 401 / 403 / 429，
         * 统计不到错误率。doFinally 在整条链路（含短路）结束后才计数，
         * 此时 AuthGlobalFilter 已把 ApiKeyScope 写进 exchange 属性。
         */
        return Ordered.HIGHEST_PRECEDENCE + 5;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return chain.filter(exchange).doFinally(signal -> record(exchange));
    }

    private void record(ServerWebExchange exchange) {
        try {
            ApiKeyScope scope = exchange.getAttribute(ApiKeyScope.ATTR);
            if (scope == null || scope.keyId() == null) {
                return;
            }
            StringRedisTemplate redis = redisProvider.getIfAvailable();
            if (redis == null) {
                return;
            }
            String key = KEY_PREFIX + scope.keyId() + ":" + LocalDateTime.now().format(MINUTE);
            redis.opsForHash().increment(key, "calls", 1);
            HttpStatusCode status = exchange.getResponse().getStatusCode();
            if (status != null && (status.value() >= 500 || status.value() == 429)) {
                redis.opsForHash().increment(key, "errors", 1);
            }
            redis.expire(key, TTL);
        } catch (Exception e) {
            log.debug("[风控] Key 调用计数失败：{}", e.getMessage());
        }
    }
}
