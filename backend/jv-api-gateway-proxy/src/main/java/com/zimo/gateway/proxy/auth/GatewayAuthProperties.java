package com.zimo.gateway.proxy.auth;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 网关鉴权配置
 */
@Data
@Component
@ConfigurationProperties(prefix = "gateway.proxy.auth")
public class GatewayAuthProperties {

    /** 是否启用网关统一鉴权 */
    private boolean enabled = true;

    /**
     * 鉴权缓存模式：auto / redis / local。
     *
     * <p>auto = Redis 可用则用 Redis，不可用回落本地 Caffeine；MySQL 始终是源数据，
     * 缓存只是副本。系统设置页（platform.auth.cache.mode）可覆盖本默认值，30 秒内生效。
     */
    private String cacheMode = "auto";

    /**
     * 免鉴权路径（Ant 风格）：登录 / 注册 / 开放认证 / 探活 / 菜单健康，
     * 以及接入 SDK 的上报通道（注册 / 心跳 / 链路采集——
     * 业务系统接入时没有控制台令牌，与 starter 端 excludePaths、
     * TraceGlobalFilter 的排除清单保持同一组路径）。
     * TODO(P1)：上报接口目前仅内网可用，后续用 X-Gateway-Client-Token 共享密钥收紧。
     */
    private List<String> skipPaths = new ArrayList<>(List.of(
            "/api/auth/login", "/api/auth/register", "/api/open/**",
            "/api/configs/public", "/health/**", "/actuator/**",
            "/api/discovery/services/register", "/api/discovery/services/heartbeat",
            "/api/discovery/services/deregister",
            "/api/trace/logs/batch", "/api/trace/spans/batch"));
}
