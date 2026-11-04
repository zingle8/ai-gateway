package com.zimo.gateway.proxy.route;

import com.zimo.gateway.proxy.mapper.RouteMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 路由规则读取。规则存在 tb_gw_route，由控制台的「路由配置」页维护。
 *
 * <p>SQL 由 {@link RouteMapper} 维护，这里只保留容错：路由表还没建时不让网关起不来。
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class RouteRepository {

    private final RouteMapper routeMapper;

    public List<GwRoute> listEnabled() {
        try {
            return routeMapper.listEnabled();
        } catch (Exception e) {
            log.warn("[路由] 读取 tb_gw_route 失败，本次不加载任何路由：{}", e.getMessage());
            return List.of();
        }
    }
}
