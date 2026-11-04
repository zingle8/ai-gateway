package com.zimo.gateway.proxy.trace;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 一条调用记录，落库到 tb_gw_trace_log。
 */
@Data
@Builder
public class TraceRecord {

    private String appId;
    private String appName;
    private String serviceName;

    /** 如 POST /order/create */
    private String api;

    /** URL 上的原始 query string（不含 ?）；表单参数原文放在 reqBody */
    private String reqParams;

    private String traceId;

    private String protocol;
    private String reqFormat;
    private String resFormat;

    private String reqIp;
    private Integer reqPort;
    private String resIp;
    private Integer resPort;

    private Integer statusCode;
    private String errType;
    private Long cost;

    private LocalDateTime reqTime;
    private LocalDateTime resTime;

    private String reqHeader;
    private String resHeader;
    private String reqBody;
    private String resBody;

    private Long reqBytes;
    private Long resBytes;
}
