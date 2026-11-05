package com.zimo.gateway.trace.entity;

import lombok.Data;

import java.time.LocalDateTime;

/** tb_gw_trace_span（按天分区，见 backend/sql/jv-api-gateway-trace-span-tb_gw_trace_span.sql） */
@Data
public class GwTraceSpan {

    private Long id;
    private String traceId;
    private String spanId;
    private String parentSpanId;
    private String kind;
    private String appId;
    private String appName;
    private String serviceName;
    private String api;
    private String peerService;
    private Integer statusCode;
    private String errType;
    private Integer cost;
    private LocalDateTime startTime;
    private LocalDateTime createTime;
}
