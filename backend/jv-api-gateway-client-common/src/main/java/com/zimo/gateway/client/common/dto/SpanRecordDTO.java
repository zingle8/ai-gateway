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
    /** 服务ID（英文唯一标识），归属字段；旧版 SDK 不传时服务端回退 serviceName */
    private String serviceId;

    /** 服务名（兼容字段）：新版 SDK 传中文名，服务端落库统一存服务ID */
    private String serviceName;
    private String api;
    private String peerService;
    private Integer statusCode;
    private String errType;
    private Integer cost;
    private LocalDateTime startTime;
}
