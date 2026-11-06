package com.zimo.gateway.proxy.registry;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * proxy 模块自注册。
 *
 * <p>平台其余 4 个 Servlet 模块用 jv-api-gateway-client-starter 自动注册，
 * 但本模块是 WebFlux 栈，SDK 的 Servlet Filter 不适用，
 * 所以自己向 discovery 心跳，保证「服务发现」页也能监控到网关本身。
 *
 * <p>心跳直连 discovery（28083），不走自己的 28090 网关转发：
 * 既避免网关故障时「自己监控不到自己」，也避免 JDK HttpClient 默认的
 * HTTP/2 升级头（Upgrade: h2c）被网关转发链路拒绝（413）。
 */
@Slf4j
@Component
public class ProxySelfRegistrar implements ApplicationRunner {

    private final HttpClient httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    @Value("${server.port:28090}")
    private int port;

    /** discovery 的直连地址（含 /api 前缀） */
    @Value("${zimo.gateway.discovery.addr:http://127.0.0.1:28083/api}")
    private String serverAddr;

    private String heartbeatBody;

    @Override
    public void run(ApplicationArguments args) {
        // @Scheduled 在首次触发前不会执行，启动时先打一次心跳让服务立即出现在列表里
        sendHeartbeat("启动注册");
    }

    /** 每 30 秒心跳一次，与 SDK 的节奏保持一致 */
    @Scheduled(fixedDelay = 30_000L, initialDelay = 30_000L)
    public void heartbeat() {
        sendHeartbeat("心跳");
    }

    private void sendHeartbeat(String action) {
        try {
            if (heartbeatBody == null) {
                heartbeatBody = "{\"appId\":\"app-api_gateway\",\"appName\":\"API网关平台\","
                        + "\"serviceId\":\"jv-api-gateway-proxy\",\"serviceName\":\"网关代理\","
                        + "\"ip\":\"127.0.0.1\","
                        + "\"port\":" + port + ",\"language\":\"Java 17 (WebFlux)\",\"version\":\"1.0.0\"}";
            }
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(serverAddr + "/discovery/services/heartbeat"))
                    .timeout(Duration.ofSeconds(5))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(heartbeatBody))
                    .build();
            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                log.info("[{}] jv-api-gateway-proxy 已上报服务发现", action);
            } else {
                log.warn("[{}] 服务发现返回 {}：{}", action, response.statusCode(), response.body());
            }
        } catch (Exception e) {
            // 心跳失败不影响转发
            log.warn("[{}] 服务发现心跳失败：{}", action, e.getMessage());
        }
    }
}
