package com.zimo.gateway.proxy.route;

import lombok.Data;

/**
 * 转发规则，对应 tb_gw_route。
 */
@Data
public class GwRouteEntity {

    private Long id;

    /** 归属应用 */
    private String appId;
    private String appName;

    /** 命中的服务名，仅用于打标和统计 */
    private String serviceName;

    /** 匹配路径，如 /order/** */
    private String pathPrefix;

    /** 转发目标，如 http://10.20.3.11:8080 */
    private String upstreamUrl;

    /** 转发前追加的路径前缀，如 /api（后端有 context-path 时必填） */
    private String prefixPath;

    /** 1 = 转发时去掉第一段路径（/order/create -> /create） */
    private Integer stripPrefix;

    /** 1 = 启用 */
    private Integer status;
}
