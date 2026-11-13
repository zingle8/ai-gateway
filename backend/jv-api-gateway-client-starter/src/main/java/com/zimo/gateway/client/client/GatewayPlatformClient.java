package com.zimo.gateway.client.client;

import com.zimo.gateway.client.common.constant.GatewayClientConstant;
import com.zimo.gateway.client.common.dto.ServiceInstanceDTO;
import com.zimo.gateway.client.common.dto.TraceLogDTO;
import com.zimo.gateway.client.properties.GatewayClientProperties;
import com.zimo.gateway.client.support.RestClientFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 网关平台服务调用客户端。
 *
 * 网关平台本身也是微服务，其他项目 / 模块可直接注入本客户端调用平台能力：
 * - 总览指标：{@link #overviewSummary()}
 * - 服务发现：{@link #pageServices(String, long, long)}
 * - 日志追踪：{@link #pageTraceLogs(Map)} / {@link #reportTrace(TraceLogDTO)}
 *
 * 不引入 Feign / 注册中心等额外依赖，任何 Spring Boot 3 项目都能直接使用。
 */
public class GatewayPlatformClient {

    private final RestClient restClient;

    public GatewayPlatformClient(GatewayClientProperties properties) {
        this.restClient = RestClientFactory.create(normalize(properties.getServerAddr()),
                5000L, properties.getClientToken(), properties.getAppId());
    }

    /** 网关总览核心指标 */
    public Map<String, Object> overviewSummary() {
        return get("/overview/summary", new HashMap<>());
    }

    /** 服务分页列表 */
    public Map<String, Object> pageServices(String keyword, long current, long size) {
        Map<String, Object> params = new HashMap<>();
        params.put("keyword", keyword);
        params.put("current", current);
        params.put("size", size);
        return get("/discovery/services", params);
    }

    /** 调用日志分页列表 */
    public Map<String, Object> pageTraceLogs(Map<String, Object> params) {
        return get("/trace/logs", params == null ? new HashMap<>() : params);
    }

    /** 上报单条调用日志（服务端只有批量端点 /trace/logs/batch，这里包成单元素列表） */
    public void reportTrace(TraceLogDTO log) {
        post("/trace/logs/batch", List.of(log));
    }

    /** 注册 / 更新服务实例 */
    public void register(ServiceInstanceDTO instance) {
        post("/discovery/services/register", instance);
    }

    private Map<String, Object> get(String path, Map<String, Object> params) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromPath(path);
        params.forEach((k, v) -> {
            if (v != null && !String.valueOf(v).isEmpty()) {
                builder.queryParam(k, v);
            }
        });
        return restClient.get()
                .uri(builder.toUriString())
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {});
    }

    private void post(String path, Object body) {
        restClient.post()
                .uri(path)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toBodilessEntity();
    }

    private static String normalize(String serverAddr) {
        if (serverAddr == null || serverAddr.isEmpty()) {
            return GatewayClientConstant.DEFAULT_SERVER_ADDR;
        }
        return serverAddr.endsWith("/") ? serverAddr.substring(0, serverAddr.length() - 1) : serverAddr;
    }
}
