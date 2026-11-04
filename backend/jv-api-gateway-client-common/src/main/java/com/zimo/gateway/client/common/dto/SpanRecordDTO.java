package com.zimo.gateway.client.common.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 链路 span 上报 DTO（与服务端 /api/trace/spans/batch 对应）。
 */
@Data
public class SpanRecordDTO {

    public static final String KIND_SERVER = "SERVER";
    public static final String KIND_CLIENT = "CLIENT";

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
}
