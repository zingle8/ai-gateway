package com.zimo.gateway.proxy.auth;

import com.zimo.gateway.proxy.mapper.AuthQueryMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 开放接口（/api/open/**）调用方约束开关（v0.2 第 6 项）。
 *
 * <p>配置项 {@code platform.open.auth.require-key}（tb_sys_config，控制台「开放认证」页维护）：
 * <ul>
 *   <li>false（默认）—— 开放接口按原白名单放行，不需要 API Key，行为与旧版本一致；</li>
 *   <li>true —— 开放接口必须携带 {@code X-Api-Key}，并走 Key 的状态 / 有效期 / IP 白名单 /
 *       服务与平台作用域校验；不接受用户 JWT，避免第三方用普通账号令牌绕过 Key 约束。</li>
 * </ul>
 * 本地缓存 10 秒，改完最迟 10 秒生效；配置表不可读时按 false 降级（保持兼容）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OpenApiKeyPolicy {

    private static final String CONFIG_KEY = "platform.open.auth.require-key";
    private static final long TTL_MILLIS = 10_000L;

    private final AuthQueryMapper mapper;

    private final AtomicLong expireAt = new AtomicLong(0);
    private volatile boolean required = false;

    public boolean required() {
        long now = System.currentTimeMillis();
        if (now < expireAt.get()) {
            return required;
        }
        try {
            String value = mapper.selectConfigValue(CONFIG_KEY);
            required = "true".equalsIgnoreCase(value == null ? "false" : value.trim());
        } catch (Exception e) {
            log.debug("[鉴权] 读取开放接口约束开关失败，按关闭处理：{}", e.getMessage());
            required = false;
        }
        expireAt.set(now + TTL_MILLIS);
        return required;
    }
}
