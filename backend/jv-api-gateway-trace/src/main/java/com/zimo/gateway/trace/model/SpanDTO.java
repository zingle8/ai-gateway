package com.zimo.gateway.trace.model;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 链路 span 上报/查询 DTO。
 *
 * <p>一跳请求 = SERVER（服务端处理）+ CLIENT（发起调用）两个 span；
 * CLIENT span 的 peerService 记录对端服务，是服务拓扑的聚合依据。
 */
@Data
public class SpanDTO {

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
