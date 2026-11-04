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
 *       server-addr: http://127.0.0.1:28080/api
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
     * 网关平台「统一入口」地址，例如 http://127.0.0.1:28080/api
     *
     * SDK 会在其后拼接 /trace/logs/batch、/discovery/services/register 等不同前缀，
     * 故该地址须能按前缀转发到对应微服务（本地可用前端 dev server，生产用 Nginx / SCG）
     */
    private String serverAddr = GatewayClientConstant.DEFAULT_SERVER_ADDR;

    /** 应用编码，如 app-order */
    private String appId = "app-demo";

    /** 应用名称，如 订单中心 */
    private String appName = "示例应用";

    /** 服务名，如 order-service */
    private String serviceName = "demo-service";

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

    /** 版本号 */
    private String version = "v1.0.0";

    private final Trace trace = new Trace();

    private final Registry registry = new Registry();

    @Data
    public static class Trace {

        /** 是否采集请求与响应 */
        private boolean enabled = true;

        /** 不采集的路径（Ant 风格） */
        private List<String> excludePaths = new ArrayList<>(List.of(
                "/actuator/**", "/error", "/favicon.ico", "/webjars/**"));

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
    public static class Registry {

        /** 是否自动注册到网关平台（自动接入） */
        private boolean enabled = true;

        /** 心跳间隔（ms） */
        private long heartbeatInterval = 30000L;
    }
}
