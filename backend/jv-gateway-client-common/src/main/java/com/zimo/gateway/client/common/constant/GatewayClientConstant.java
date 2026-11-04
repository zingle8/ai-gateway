package com.zimo.gateway.client.common.constant;

/**
 * 网关接入 SDK 公共常量
 */
public final class GatewayClientConstant {

    private GatewayClientConstant() {
    }

    /** 链路追踪 ID 请求头 */
    public static final String HEADER_TRACE_ID = "X-Gateway-Trace-Id";

    /** W3C Trace Context 标准头（00-<traceId>-<spanId>-01），与 OTel 生态兼容 */
    public static final String HEADER_TRACEPARENT = "traceparent";

    /** 平台内部调用标识头 */
    public static final String HEADER_CLIENT_TOKEN = "X-Gateway-Client-Token";

    /** 批量上报调用日志 */
    public static final String PATH_TRACE_BATCH = "/trace/logs/batch";

    /** 批量上报链路 span */
    public static final String PATH_SPAN_BATCH = "/trace/spans/batch";

    /** 服务注册（自动接入） */
    public static final String PATH_SERVICE_REGISTER = "/discovery/services/register";

    /** 服务心跳 */
    public static final String PATH_SERVICE_HEARTBEAT = "/discovery/services/heartbeat";

    /** 服务下线 */
    public static final String PATH_SERVICE_DEREGISTER = "/discovery/services/deregister";

    /** 默认配置前缀 */
    public static final String CONFIG_PREFIX = "zimo.gateway.client";

    /**
     * 默认网关平台地址。
     *
     * 注意：这里是「平台统一入口」地址，不是某个具体微服务的地址——
     * SDK 会在其后拼接不同前缀（{@link #PATH_TRACE_BATCH} 走 jv-gateway-trace、
     * {@link #PATH_SERVICE_REGISTER} 走 jv-gateway-discovery），
     * 因此需要由统一入口按前缀转发到对应微服务：
     *   本地：直接使用前端 dev server（已配置 /api 前缀代理）
     *   生产：使用 Nginx / Spring Cloud Gateway 转发
     */
    public static final String DEFAULT_SERVER_ADDR = "http://127.0.0.1:28080/api";

    /** 单条报文体最大采集长度（超出截断） */
    public static final int DEFAULT_MAX_BODY_SIZE = 8192;

    /** 上报队列容量（满了直接丢弃，绝不阻塞业务） */
    public static final int DEFAULT_QUEUE_SIZE = 1024;
}
