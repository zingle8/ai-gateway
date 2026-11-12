package com.zimo.gateway.client.properties;

import com.zimo.gateway.client.common.constant.GatewayClientConstant;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 网关接入配置（前缀：zimo.gateway.client）
 *
 * <pre>
 * zimo:
 *   gateway:
 *     client:
 *       enabled: true
 *       server-addr: http://127.0.0.1:28090/api
 *       app-id: app-order
 *       app-name: 订单中心
 *       service-name: order-service
 * </pre>
 */
@Data
@ConfigurationProperties(prefix = GatewayClientProperties.PREFIX)
public class GatewayClientProperties {

    public static final String PREFIX = "zimo.gateway.client";

    /** 是否启用接入 SDK */
    private boolean enabled = true;

    /**
     * 网关平台「统一入口」地址，例如 http://127.0.0.1:28090/api
     *
     * SDK 会在其后拼接 /trace/logs/batch、/discovery/services/register 等不同前缀，
     * 故该地址须能按前缀转发到对应微服务（本地可用前端 dev server，生产用 Nginx / SCG）
     */
    private String serverAddr = GatewayClientConstant.DEFAULT_SERVER_ADDR;

    /** 应用编码，如 app-order */
    private String appId = "app-demo";

    /** 应用名称，如 订单中心 */
    private String appName = "示例应用";

    /** 服务ID（英文唯一标识），如 order-service；留空时回退使用 serviceName（兼容旧配置） */
    private String serviceId = "";

    /** 服务名（中文显示名），如 订单服务；留空时服务端显示同 serviceId */
    private String serviceName = "demo-service";

    /**
     * 生效的服务ID（英文唯一标识）：service-id 有值用 service-id，
     * 否则回退 service-name（兼容只配 service-name 的旧接入方）。
     * 注册幂等键与上报归属字段统一用这个值。
     */
    public String effectiveServiceId() {
        return serviceId != null && !serviceId.isBlank() ? serviceId : serviceName;
    }

    /** 服务端口，未配置时自动取 server.port */
    private Integer port;

    /**
     * 注册用的本机 IP。
     *
     * <p>留空则自动探测（枚举网卡取第一个物理网卡的 IPv4）。
     * 多网卡机器（装了 Docker / VPN / 虚拟机）上自动探测可能选中虚拟网卡，
     * 此时请显式指定为平台可达的那块网卡 IP。</p>
     */
    private String ip;

    /** 语言类型 */
    private String language = "Java 17";

    /**
     * 上报共享密钥（X-Gateway-Client-Token）：注册 / 心跳 / 下线 / 采集上报请求携带，
     * 网关（gateway.proxy.auth.client-token 非空时）校验，防止内网伪造注册与上报。
     * 默认空 = 不携带（对应网关不校验）。生产环境务必用环境变量注入并与网关一致。
     */
    private String clientToken = "";

    private final Auth auth = new Auth();

    /** 版本号 */
    private String version = "v1.0.0";

    private final Trace trace = new Trace();

    private final Registry registry = new Registry();

    private final Metrics metrics = new Metrics();

    /**
     * 主机运行指标上报（v0.2 第 10 项）：每 60 秒采集一次 CPU / 内存 / JVM / 磁盘 / 网络，
     * 有 NVIDIA GPU 时附带 GPU 指标，POST 到 {server-addr}/discovery/metrics/batch。
     */
    @Data
    public static class Metrics {

        /** 关闭开关：zimo.gateway.client.metrics.enabled=false */
        private boolean enabled = true;

        /** 采集间隔（ms），最小 10 秒 */
        private long interval = 60_000L;

        /** 是否尝试采集 GPU（调用 nvidia-smi，无 GPU 自动跳过） */
        private boolean gpuEnabled = true;
    }

    @Data
    public static class Trace {

        /** 是否采集请求与响应 */
        private boolean enabled = true;

        /** 不采集的路径（Ant 风格）。SDK 自身的注册/心跳/上报调用必须排除，
         * 否则每次批量上报又被采集成一条调用日志/span，形成自引用噪音 */
        private List<String> excludePaths = new ArrayList<>(List.of(
                "/actuator/**", "/error", "/favicon.ico", "/webjars/**",
                "/api/trace/logs/batch", "/api/trace/spans/batch",
                "/api/discovery/services/register", "/api/discovery/services/heartbeat",
                "/api/discovery/services/deregister", "/api/discovery/metrics/batch"));

        /** 单条报文体最大采集长度，超出截断 */
        private int maxBodySize = 8192;

        /** 采样率 0~1，1 表示全采 */
        private double sampleRate = 1.0d;

        /** 批量上报条数阈值 */
        private int batchSize = 50;

        /** 定时上报间隔（ms） */
        private long flushInterval = 3000L;

        /** 单次上报超时（ms） */
        private long reportTimeout = 5000L;
    }

    @Data
    public static class Auth {

        /** 是否启用入口鉴权：调用本服务的请求需持有效 token / API Key（回调网关开放验签接口验证） */
        private boolean enabled = false;

        /** 验签端点；默认 {server-addr}/open/auth/token/verify（同时支持用户 JWT 与 API Key） */
        private String verifyUrl = "";

        /** 豁免路径（Ant 风格）：健康检查、探活等无需凭证 */
        private List<String> exemptPaths = new ArrayList<>(List.of("/health/**", "/actuator/**", "/error"));

        /** 验签结果缓存秒数：同一 token 在此期间不重复回调网关 */
        private int cacheSeconds = 60;

        /** 单次验签超时（ms） */
        private long timeoutMs = 3000L;

        /**
         * 网关不可达时的策略：false = 拒绝（安全优先，默认）；true = 放行（可用性优先）。
         * 注意：即使放行，验签失败本身也会作为调用日志上报，网关侧可见。
         */
        private boolean failOpen = false;
    }

    @Data
    public static class Registry {

        /** 是否自动注册到网关平台（自动接入） */
        private boolean enabled = true;

        /** 心跳间隔（ms） */
        private long heartbeatInterval = 30000L;
    }
}
