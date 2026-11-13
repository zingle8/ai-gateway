package com.zimo.gateway.proxy.health;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 平台各后端服务的运行状态探测，供前端侧边栏菜单状态标识使用。
 *
 * <pre>
 * 状态语义
 *   UP      绿色：可达且返回非 5xx（4xx 也算活着，如 401 未登录）
 *   DOWN    灰色：连接拒绝 / 超时 / 无响应（服务没起来或检查不到）
 *   ERROR   红色：可达但返回 5xx（进程在、但运行有问题）
 * </pre>
 *
 * <p>探测地址默认按服务名写死（不经网关自身），可被基础设施配置覆盖：
 * {@code infra.service.<服务名>=http://...}（gw-platform-infra.properties，v0.2 配置集中）。
 */
@Slf4j
@Service
public class MenuHealthService {

    /** 菜单 → 后端服务探测地址默认值（与前端 menus 的 service 字段一一对应） */
    private static final Map<String, String> DEFAULT_PROBES = new LinkedHashMap<>();

    static {
        DEFAULT_PROBES.put("jv-api-gateway-user", "http://127.0.0.1:28081/api/auth/login");
        DEFAULT_PROBES.put("jv-api-gateway-overview", "http://127.0.0.1:28082/api/overview/summary");
        DEFAULT_PROBES.put("jv-api-gateway-discovery", "http://127.0.0.1:28083/api/discovery/services");
        DEFAULT_PROBES.put("jv-api-gateway-trace", "http://127.0.0.1:28084/api/trace/logs");
        DEFAULT_PROBES.put("jv-api-gateway-config", "http://127.0.0.1:28085/api/configs/public");
        DEFAULT_PROBES.put("jv-api-gateway-scheduler", "http://127.0.0.1:28087/api/scheduler/jobs");
        DEFAULT_PROBES.put("jv-api-gateway-proxy", "http://127.0.0.1:28090/health/menu-services");
    }

    /** 基础设施配置可整体覆盖：infra.service.<服务名>=http://host:port/... */
    private final Map<String, String> probes = new LinkedHashMap<>(DEFAULT_PROBES);

    public MenuHealthService(org.springframework.core.env.Environment env) {
        for (String svc : DEFAULT_PROBES.keySet()) {
            String override = env.getProperty("infra.service." + svc);
            if (override != null && !override.isBlank()) {
                probes.put(svc, override.trim());
                log.info("[服务探测] {} 探测地址由基础设施配置覆盖：{}", svc, override.trim());
            }
        }
    }

    private final WebClient webClient = WebClient.builder()
            .clientConnector(new org.springframework.http.client.reactive.ReactorClientHttpConnector(
                    reactor.netty.http.client.HttpClient.create()
                            .responseTimeout(Duration.ofSeconds(2))
                            .option(io.netty.channel.ChannelOption.CONNECT_TIMEOUT_MILLIS, 1000)))
            .build();

    /** serviceName → 状态结果 */
    private final Map<String, Status> cache = new ConcurrentHashMap<>();

    public record Status(String service, String status, long latencyMs, String detail, LocalDateTime checkedAt) {
    }

    @PostConstruct
    public void init() {
        probes.keySet().forEach(name -> cache.put(name, new Status(name, "DOWN", -1, "等待首次探测", null)));
    }

    /** 30 秒一轮，全部并发探测 */
    @Scheduled(fixedDelay = 30_000L, initialDelay = 3_000L)
    public void probe() {
        List<Mono<Void>> tasks = probes.entrySet().stream()
                .map(entry -> probeOne(entry.getKey(), entry.getValue()))
                .toList();
        Mono.when(tasks).subscribe(null, e -> log.debug("[健康探测] {}", e.getMessage()));
    }

    private Mono<Void> probeOne(String service, String url) {
        long start = System.currentTimeMillis();
        return webClient.get().uri(url).exchangeToMono(response -> {
            int code = response.statusCode().value();
            long cost = System.currentTimeMillis() - start;
            String status = code >= 500 ? "ERROR" : "UP";
            cache.put(service, new Status(service, status, cost,
                    "HTTP " + code, LocalDateTime.now()));
            return response.releaseBody();
        }).timeout(Duration.ofSeconds(2)).onErrorResume(e -> {
            long cost = System.currentTimeMillis() - start;
            cache.put(service, new Status(service, "DOWN", cost,
                    "不可达：" + e.getClass().getSimpleName(), LocalDateTime.now()));
            return Mono.empty();
        }).then();
    }

    public List<Status> snapshot() {
        return probes.keySet().stream()
                .map(name -> cache.getOrDefault(name, new Status(name, "DOWN", -1, "未探测", null)))
                .toList();
    }
}
