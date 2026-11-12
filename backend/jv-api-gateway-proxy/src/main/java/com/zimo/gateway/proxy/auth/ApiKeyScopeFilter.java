package com.zimo.gateway.proxy.auth;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.zimo.gateway.proxy.route.GwRouteEntity;
import com.zimo.gateway.proxy.route.RouteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

/**
 * API Key 作用域校验（v0.2 第 6 项）。
 *
 * <p>Key 校验通过后，{@link AuthGlobalFilter} 会把约束写进 exchange 属性；
 * 本过滤器按请求路径匹配到目标路由（tb_gw_route，10 秒缓存），再比对：
 * <ul>
 *   <li>服务白名单 —— 目标路由的 service_name 不在列表内 → 403；</li>
 *   <li>平台白名单 —— 目标路由的 app_id 不在列表内 → 403。</li>
 * </ul>
 * 白名单为空表示不限；匹配不到路由（404 场景）不在此拦截，交给后续路由解析。
 *
 * <p>排在鉴权之后（{@code HIGHEST_PRECEDENCE + 15}）、限流之前（+20），
 * 越界请求不消耗限流令牌，也不产生调用日志（链路采集过滤器更靠后）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApiKeyScopeFilter implements GlobalFilter, Ordered {

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    private final RouteRepository routeRepository;

    /** 路由表 10 秒缓存：作用域校验是每请求动作，不能每次都查库 */
    private final Cache<String, List<GwRouteEntity>> routeCache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofSeconds(10))
            .maximumSize(1)
            .build();

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 15;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        Object attr = exchange.getAttributes().get(ApiKeyScope.ATTR);
        if (!(attr instanceof ApiKeyScope scope)
                || (!scope.restrictsService() && !scope.restrictsPlatform())) {
            return chain.filter(exchange);
        }
        ServerHttpRequest request = exchange.getRequest();
        GwRouteEntity route = matchRoute(request.getPath().value());
        if (route == null) {
            return chain.filter(exchange);
        }
        if (scope.restrictsService() && !scope.allowedServices().contains(route.getServiceName())) {
            return deny(exchange, scope, "服务「" + route.getServiceName() + "」不在该 API Key 的授权范围内");
        }
        if (scope.restrictsPlatform() && !scope.allowedPlatforms().contains(route.getAppId())) {
            return deny(exchange, scope, "平台「" + route.getAppName() + "」不在该 API Key 的授权范围内");
        }
        return chain.filter(exchange);
    }

    /** 最长前缀匹配（/api/order/** 优先于 /api/**） */
    private GwRouteEntity matchRoute(String path) {
        List<GwRouteEntity> routes = routeCache.get("all", key -> routeRepository.listEnabled());
        GwRouteEntity best = null;
        for (GwRouteEntity route : routes) {
            String prefix = route.getPathPrefix();
            if (prefix == null || !PATH_MATCHER.match(prefix, path)) {
                continue;
            }
            if (best == null || prefix.length() > best.getPathPrefix().length()) {
                best = route;
            }
        }
        return best;
    }

    private Mono<Void> deny(ServerWebExchange exchange, ApiKeyScope scope, String reason) {
        log.info("[鉴权] API Key {} 越界访问 {} {}：{}", scope.keyId(),
                exchange.getRequest().getMethod(), exchange.getRequest().getPath().value(), reason);
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.FORBIDDEN);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"code\":403,\"message\":\"" + reason + "\",\"data\":null}";
        DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }
}
