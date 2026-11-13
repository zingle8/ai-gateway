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
     * SDK 上报共享密钥（X-Gateway-Client-Token）：非空时对上报路径
     * （clientTokenPaths）校验请求头，不匹配拒绝——防止内网伪造注册与上报；
     * 为空 = 不校验（向后兼容）。yml 中配 ${GATEWAY_CLIENT_TOKEN:dev-gateway-token}，
     * 生产环境务必用环境变量覆盖，且与接入方 zimo.gateway.client.client-token 一致。
     */
    private String clientToken = "";

    /** 需校验共享密钥的上报路径（在 skipPaths 放行之后做二次校验，与 skipPaths 中的上报通道一一对应） */
    private List<String> clientTokenPaths = new ArrayList<>(List.of(
            "/api/discovery/services/register", "/api/discovery/services/heartbeat",
            "/api/discovery/services/deregister", "/api/trace/logs/batch", "/api/trace/spans/batch",
            "/api/discovery/metrics/batch", "/api/discovery/metrics/hostnames",
            "/api/ws/frame/batch", "/api/ws/session/batch"));

    /**
     * 免鉴权路径（Ant 风格）：登录 / 注册 / 开放认证 / 探活 / 菜单健康，
     * 以及接入 SDK 的上报通道（注册 / 心跳 / 链路采集——
     * 业务系统接入时没有控制台令牌，与 starter 端 excludePaths、
     * TraceGlobalFilter 的排除清单保持同一组路径）。
     */
    private List<String> skipPaths = new ArrayList<>(List.of(
            "/api/auth/login", "/api/auth/register",
            // 登录因子（v0.2 第 4 项）：验证码 / 动态码 / 短信·邮件码 / 扫码，均在登录前调用
            "/api/auth/captcha/**", "/api/auth/otp/**", "/api/auth/qr/**",
            "/api/auth/login-methods",
            // SSO OIDC 协议端点（v0.2 第 15 项）：authorize/token/userinfo/元数据/jwks 匿名
            "/api/oauth/**", "/api/.well-known/**",
            "/api/open/**",
            "/api/configs/public", "/health/**", "/actuator/**",
            "/api/discovery/services/register", "/api/discovery/services/heartbeat",
            "/api/discovery/services/deregister",
            "/api/trace/logs/batch", "/api/trace/spans/batch"));
}
