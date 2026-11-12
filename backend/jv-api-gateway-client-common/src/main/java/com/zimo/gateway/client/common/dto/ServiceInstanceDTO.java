package com.zimo.gateway.client.common.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 服务实例注册模型（自动接入：业务系统启动后向 jv-api-gateway-discovery 注册自身）
 */
@Data
public class ServiceInstanceDTO {

    /** 应用编码，如 app-order */
    private String appId;

    /** 应用名称，如 订单中心 */
    private String appName;

    /** 服务ID（英文唯一标识），如 order-service；未配置时服务端回退用 serviceName */
    private String serviceId;

    /** 服务名（中文显示名），如 订单服务；可空，空则显示同 serviceId */
    private String serviceName;

    /** 实例 IP */
    private String ip;

    /** 主机名 / K8s pod name（v0.2 第 11 项部署关联用，空则由服务端用 IP 兜底） */
    private String hostname;

    /** 服务端口 */
    private Integer port;

    /** 语言类型，如 Java 17 */
    private String language;

    /** 版本号，如 v2.4.0 */
    private String version;

    /** 注册时间 */
    private LocalDateTime registerTime;
}
