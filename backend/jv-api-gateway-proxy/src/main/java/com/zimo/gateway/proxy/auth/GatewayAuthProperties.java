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

    /** 免鉴权路径（Ant 风格），默认放行登录 / 注册与探活 */
    private List<String> skipPaths = new ArrayList<>(List.of(
            "/api/auth/login", "/api/auth/register", "/actuator/**"));
}
