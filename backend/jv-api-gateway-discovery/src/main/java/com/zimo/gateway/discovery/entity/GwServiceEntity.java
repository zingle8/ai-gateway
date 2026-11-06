package com.zimo.gateway.discovery.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 注册服务（表 tb_gw_service，字段蛇形命名由 MyBatis 驼峰映射自动对应）
 */
@Data
public class GwServiceEntity {

    private Long id;

    /** 应用编码，如 app-order */
    private String appId;

    /** 应用名称，如 订单中心 */
    private String appName;

    /** 服务ID（英文唯一标识），如 order-service */
    private String serviceId;

    /** 服务名（中文显示名），如 订单服务；空则展示同 serviceId */
    private String serviceName;

    /** 实例 IP，由 SDK 心跳上报（多网卡机器建议在 SDK 侧显式配置） */
    private String ip;

    /** 服务端口 */
    private Integer port;

    /** 语言类型，如 Java 17 */
    private String language;

    /** 版本号，如 v2.4.0 */
    private String version;

    /** 实时 QPS */
    private Integer qps;

    /** 平均响应时间（ms） */
    private Integer avgRt;

    /** 区间调用 / 请求次数（tb_gw_trace_log 按所选时间区间统计） */
    private Long calls;

    /** 已登记接口数（tb_gw_api 按服务ID关联） */
    private Integer apiCount;

    /** 服务状态：健康 / 亚健康 / 异常 / 离线 */
    private String status;

    /** 入站流量，如 3.8 GB */
    private String inFlow;

    /** 出网流量，如 26.4 GB */
    private String outFlow;

    /** 实例数 */
    private Integer instances;

    /** 注册时间 */
    private LocalDateTime registerTime;

    /** 最近心跳时间（自动接入的服务持续刷新） */
    private LocalDateTime lastHeartbeatTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    /** 逻辑删除：0 正常，1 已删除（SQL 里显式过滤） */
    private Integer deleted;
}
