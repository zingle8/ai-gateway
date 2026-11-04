package com.zimo.gateway.discovery.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 网关转发路由（表 tb_gw_route）。
 *
 * <p>由控制台「路由配置」维护，jv-api-gateway-proxy 每 30 秒热加载。
 */
@Data
public class GwRoute {

    private Long id;

    /** 归属应用编码 */
    private String appId;

    /** 归属应用名称 */
    private String appName;

    /** 命中的服务名（仅用于采集打标） */
    private String serviceName;

    /** 匹配路径，如 /order/** */
    private String pathPrefix;

    /** 转发目标，如 http://10.20.3.11:8080 */
    private String upstreamUrl;

    /** 转发前追加的路径前缀，如 /api（后端有 context-path 时使用） */
    private String prefixPath;

    /** 1 = 转发时去掉第一段路径 */
    private Integer stripPrefix;

    /** 1 = 启用 */
    private Integer status;

    private String remark;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    /** 逻辑删除：0 正常，1 已删除（SQL 里显式过滤） */
    private Integer deleted;
}
