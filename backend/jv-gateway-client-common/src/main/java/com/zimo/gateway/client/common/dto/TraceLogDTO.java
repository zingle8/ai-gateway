package com.zimo.gateway.client.common.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 调用日志上报模型（由业务系统 SDK 采集后批量上报到 jv-gateway-trace）
 */
@Data
public class TraceLogDTO {

    /** 应用编码，如 app-order */
    private String appId;

    /** 应用名称，如 订单中心 */
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

    /** 请求格式：JSON / form / XML 等 */
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

    /** 网关错误类型 */
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
}
