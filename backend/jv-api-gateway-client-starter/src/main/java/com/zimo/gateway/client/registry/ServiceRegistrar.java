package com.zimo.gateway.client.registry;

import com.zimo.gateway.client.common.constant.GatewayClientConstant;
import com.zimo.gateway.client.common.dto.ServiceInstanceDTO;
import com.zimo.gateway.client.properties.GatewayClientProperties;
import com.zimo.gateway.client.support.LocalIps;
import com.zimo.gateway.client.support.RestClientFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.net.InetAddress;
import java.time.LocalDateTime;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 自动接入：业务系统启动后把自身实例注册到网关平台（jv-api-gateway-discovery），
 * 运行期间按固定间隔上报心跳，进程退出时主动下线。
 *
 * 对应控制台「接入中心 → 自动接入」。
 */
@Slf4j
public class ServiceRegistrar implements ApplicationRunner, DisposableBean {

    private final GatewayClientProperties properties;
    private final Environment environment;
    private final RestClient restClient;
    private final ScheduledExecutorService executor;

    /** 注册是否成功；失败时心跳周期会自动补注册（服务端按 serviceId upsert，幂等） */
    private volatile boolean registered = false;

    public ServiceRegistrar(GatewayClientProperties properties, Environment environment) {
        this.properties = properties;
        this.environment = environment;
        this.restClient = RestClientFactory.create(normalize(properties.getServerAddr()),
                5000L, properties.getClientToken(), properties.getAppId());
        this.executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "zimo-gateway-registry");
            thread.setDaemon(true);
            return thread;
        });
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.isEnabled() || !properties.getRegistry().isEnabled()) {
            return;
        }
        register();
        long interval = Math.max(5000L, properties.getRegistry().getHeartbeatInterval());
        executor.scheduleWithFixedDelay(this::heartbeatQuietly, interval, interval, TimeUnit.MILLISECONDS);
    }

    /** 注册 / 更新服务实例 */
    public void register() {
        ServiceInstanceDTO instance = buildInstance();
        try {
            restClient.post()
                    .uri(GatewayClientConstant.PATH_SERVICE_REGISTER)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(instance)
                    .retrieve()
                    .toBodilessEntity();
            registered = true;
            log.info("已注册到网关平台：{} ({}) {}: {}",
                    instance.getAppId(), instance.getServiceId(), instance.getIp(), instance.getPort());
        } catch (Exception e) {
            // 标记未注册：下个心跳周期由 heartbeatQuietly 自动补注册，
            // 避免启动顺序早于网关（如本平台各服务）时一次失败就永久"离线"
            registered = false;
            log.warn("注册到网关平台失败（不影响业务启动，下个心跳周期自动重试）：{}", e.getMessage());
        }
    }

    /** 心跳 */
    public void heartbeat() {
        ServiceInstanceDTO instance = buildInstance();
        try {
            restClient.post()
                    .uri(GatewayClientConstant.PATH_SERVICE_HEARTBEAT)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(instance)
                    .retrieve()
                    .toBodilessEntity();
            registered = true;
        } catch (Exception e) {
            registered = false;
            log.warn("网关平台心跳失败，下个周期将尝试重新注册：{}", e.getMessage());
        }
    }

    @Override
    public void destroy() {
        executor.shutdownNow();
        if (properties.isEnabled() && properties.getRegistry().isEnabled()) {
            deregister();
        }
    }

    /** 主动下线 */
    private void deregister() {
        ServiceInstanceDTO instance = buildInstance();
        try {
            restClient.method(org.springframework.http.HttpMethod.DELETE)
                    .uri(GatewayClientConstant.PATH_SERVICE_DEREGISTER)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(instance)
                    .retrieve()
                    .toBodilessEntity();
            log.info("已从网关平台下线：{} {}", instance.getServiceId(), instance.getPort());
        } catch (Exception e) {
            log.debug("从网关平台下线失败：{}", e.getMessage());
        }
    }

    private void heartbeatQuietly() {
        try {
            // 未注册成功（含启动时失败、心跳失联）时先补注册，注册成功才发心跳
            if (!registered) {
                register();
                if (!registered) {
                    return;
                }
            }
            heartbeat();
        } catch (Exception e) {
            log.debug("心跳异常：{}", e.getMessage());
        }
    }

    private ServiceInstanceDTO buildInstance() {
        ServiceInstanceDTO instance = new ServiceInstanceDTO();
        instance.setAppId(properties.getAppId());
        instance.setAppName(properties.getAppName());
        // 服务ID（英文唯一标识）：未配置时回退 serviceName，兼容旧版配置
        String serviceId = properties.effectiveServiceId();
        instance.setServiceId(serviceId);
        // 服务名（中文显示名）：未配置时与服务ID一致
        instance.setServiceName(properties.getServiceName() != null && !properties.getServiceName().isBlank()
                ? properties.getServiceName() : serviceId);
        // 显式配置优先，多网卡机器上自动探测可能选中虚拟网卡
        instance.setIp(properties.getIp() != null && !properties.getIp().isEmpty()
                ? properties.getIp() : LocalIps.get());
        instance.setPort(resolvePort());
        // 部署关联（v0.2 第 11 项）：上报主机名 / pod name，平台按 hostname 聚合部署清单
        instance.setHostname(localHostname());
        instance.setLanguage(properties.getLanguage());
        instance.setVersion(properties.getVersion());
        instance.setRegisterTime(LocalDateTime.now());
        return instance;
    }

    /** 主机名：优先环境变量 HOSTNAME（K8s 下即 pod name），失败回退 unknown-host */
    private String localHostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            String env = System.getenv("HOSTNAME");
            return env == null || env.isBlank() ? "unknown-host" : env;
        }
    }

    private static String normalize(String serverAddr) {
        if (serverAddr == null || serverAddr.isEmpty()) {
            return GatewayClientConstant.DEFAULT_SERVER_ADDR;
        }
        return serverAddr.endsWith("/") ? serverAddr.substring(0, serverAddr.length() - 1) : serverAddr;
    }

    /**
     * 端口优先级：显式配置 &gt; server.port &gt; 8080
     */
    private int resolvePort() {
        if (properties.getPort() != null) {
            return properties.getPort();
        }
        if (environment != null) {
            try {
                return Integer.parseInt(String.valueOf(environment.getProperty("server.port", "8080")));
            } catch (Exception ignored) {
                return 8080;
            }
        }
        return 8080;
    }
}
