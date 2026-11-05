package com.zimo.gateway.proxy.mapper;

import com.zimo.gateway.proxy.route.GwRouteEntity;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 网关转发规则读取（原生 MyBatis，SQL 见 resources/mapper/RouteMapper.xml）。
 */
@Mapper
public interface RouteMapper {

    /** 已启用且未逻辑删除的路由，按 id 升序，保证转发优先级稳定 */
    List<GwRouteEntity> listEnabled();
}
