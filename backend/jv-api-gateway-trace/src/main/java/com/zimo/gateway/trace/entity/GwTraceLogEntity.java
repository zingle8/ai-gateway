package com.zimo.gateway.trace.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 网关调用日志（表 tb_gw_trace_log，字段蛇形命名由 MyBatis 驼峰映射自动对应）
 */
@Data
public class GwTraceLogEntity {

    private Long id;

    private String appId;

    private String appName;

    /** 命中的后端服务 */
    private String serviceName;

    /** 接口，如 POST /api/v1/order/create */
    private String api;

    /** URL 上的原始 query string；表单参数原文在 reqBody */
    private String reqParams;

    /** 链路追踪 ID */
    private String traceId;

    /** 协议：HTTP/1.1 / HTTP/2 / gRPC / WebSocket */
    private String protocol;

    /** 请求格式：JSON / Protobuf / form 等 */
    private String reqFormat;

    /** 返回格式 */
    private String resFormat;

    /** 调用方 IP */
    private String reqIp;

    /** 调用方端口 */
    private Integer reqPort;

    /** 后端实例 IP */
    private String resIp;

    /** 后端实例端口 */
    private Integer resPort;

    /** 响应状态码 */
    private Integer statusCode;

    /** 网关错误类型：无 / 鉴权失败 / 路由失败 / 限流拒绝 / 参数校验失败 / 上游响应超时 / 上游响应异常 */
    private String errType;

    /** 耗时（ms） */
    private Integer cost;

    private LocalDateTime reqTime;

    private LocalDateTime resTime;

    /** 请求 Header 原文 */
    private String reqHeader;

    /** 响应 Header 原文 */
    private String resHeader;

    /** 请求报文体 */
    private String reqBody;

    /** 返回报文体 */
    private String resBody;

    /** 请求字节数 */
    private Long reqBytes;

    /** 返回字节数 */
    private Long resBytes;

    private LocalDateTime createTime;

    /** 逻辑删除：0 正常，1 已删除（SQL 里显式过滤） */
    private Integer deleted;
}
