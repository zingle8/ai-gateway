package com.zimo.gateway.proxy.trace;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 调用链路 span：一次「处理」或「发起调用」的最小单元。
 *
 * <p>一跳请求在网关产生两个 span：
 * <ul>
 *   <li>SERVER —— 调用方 → 网关（parent 为上游传播来的 span，链路入口为 NULL）</li>
 *   <li>CLIENT —— 网关 → 下游服务（peerService 记录目标服务，是服务拓扑的聚合依据）</li>
 * </ul>
 *
 * <p>与 {@link TraceRecord}（报文审计）分离：span 只存链路分析所需的最小字段。
 */
@Data
@Builder
public class SpanRecord {

    /** SERVER=服务端处理 / CLIENT=发起调用 */
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
