package com.zimo.gateway.proxy.route;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.event.RefreshRoutesEvent;
import org.springframework.cloud.gateway.filter.FilterDefinition;
import org.springframework.cloud.gateway.handler.predicate.PredicateDefinition;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionLocator;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 路由来源：数据库（原生 MyBatis 读取）。
 *
 * <p>每 30 秒重新读取 tb_gw_route 并触发路由刷新，
 * 这样控制台改完规则不用重启网关。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DbRouteDefinitionLocator implements RouteDefinitionLocator {

    private final RouteRepository routeRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public Flux<RouteDefinition> getRouteDefinitions() {
        List<RouteDefinition> definitions = new ArrayList<>();
        for (GwRoute route : routeRepository.listEnabled()) {
            try {
                definitions.add(toDefinition(route));
            } catch (Exception e) {
                log.warn("[路由] 规则 {} 转换失败（{} -> {}）：{}",
                        route.getId(), route.getPathPrefix(), route.getUpstreamUrl(), e.getMessage());
            }
        }
        return Flux.fromIterable(definitions);
    }

    /** 定时触发刷新，路由变更无需重启 */
    @Scheduled(fixedDelay = 30_000L, initialDelay = 10_000L)
    public void refreshRoutes() {
        eventPublisher.publishEvent(new RefreshRoutesEvent(this));
    }

    private RouteDefinition toDefinition(GwRoute route) {
        RouteDefinition definition = new RouteDefinition();
        definition.setId("gw-route-" + route.getId());
        definition.setUri(URI.create(route.getUpstreamUrl().trim()));

        // 采集侧靠这些元数据标注 app / 服务名
        Map<String, Object> metadata = new HashMap<>(4);
        metadata.put("appId", route.getAppId());
        metadata.put("appName", route.getAppName());
        metadata.put("serviceName", route.getServiceName());
        definition.setMetadata(metadata);

        PredicateDefinition predicate = new PredicateDefinition();
        predicate.setName("Path");
        predicate.addArg("pattern", route.getPathPrefix().trim());
        definition.setPredicates(List.of(predicate));

        List<FilterDefinition> filters = new ArrayList<>(2);
        // 先去掉用于匹配的路径段：/gw-demo/order/create -> /order/create
        if (route.getStripPrefix() != null && route.getStripPrefix() == 1) {
            filters.add(new FilterDefinition("StripPrefix=1"));
        }
        // 再补后端 context-path：/order/create -> /api/order/create
        if (route.getPrefixPath() != null && !route.getPrefixPath().isBlank()) {
            filters.add(new FilterDefinition("PrefixPath=" + route.getPrefixPath().trim()));
        }
        if (!filters.isEmpty()) {
            definition.setFilters(filters);
        }
        return definition;
    }
}
