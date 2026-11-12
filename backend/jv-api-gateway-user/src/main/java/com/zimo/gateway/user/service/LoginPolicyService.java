package com.zimo.gateway.user.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 登录策略读取（tb_sys_config JSON，控制台「安全策略」「登录方式」页维护）。
 *
 * <ul>
 *   <li>platform.login.methods —— 登录因子开关（factors[].key=username/email/...）+ 邮件/短信通道</li>
 *   <li>platform.security.policy —— 失败锁定阈值 / 锁定时长 / MFA 开关与适用范围等</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoginPolicyService {

    public static final String POLICY_KEY = "platform.security.policy";
    public static final String METHODS_KEY = "platform.login.methods";

    private final PlatformConfigReader configReader;
    private final ObjectMapper objectMapper;

    /** 密码登录通道是否开启（登录因子 username.enabled，默认开启） */
    public boolean passwordChannelEnabled() {
        Map<String, Object> methods = readMap(METHODS_KEY);
        Object factors = methods.get("factors");
        if (factors instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> factor && "username".equals(factor.get("key"))) {
                    return !Boolean.FALSE.equals(factor.get("enabled"));
                }
            }
        }
        return true;
    }

    /** 登录失败锁定阈值（次） */
    public int lockThreshold() {
        return intValue(securityPolicy().get("lockThreshold"), 5, 1, 20);
    }

    /** 锁定时长（分钟） */
    public int lockMinutes() {
        return intValue(securityPolicy().get("lockMinutes"), 30, 1, 1440);
    }

    /**
     * MFA 是否适用于该账号：
     * mfa.enabled 且 scope 覆盖（ALL / ADMIN_ONLY + ADMIN 角色 / SENSITIVE 同 ADMIN_ONLY）。
     */
    public boolean mfaRequiredFor(String role) {
        String scope = String.valueOf(securityPolicy().getOrDefault("mfaScope", "NONE"));
        return switch (scope) {
            case "ALL" -> true;
            case "ADMIN" -> "ADMIN".equals(role);
            default -> false; // NONE：不强制
        };
    }

    private Map<String, Object> securityPolicy() {
        return readMap(POLICY_KEY);
    }

    private Map<String, Object> readMap(String key) {
        String raw = configReader.get(key, "");
        if (raw == null || raw.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(raw, new TypeReference<>() {
            });
        } catch (Exception e) {
            log.warn("[策略] 配置 {} 解析失败：{}", key, e.getMessage());
            return Map.of();
        }
    }

    private static int intValue(Object value, int fallback, int min, int max) {
        if (value instanceof Number n) {
            return Math.max(min, Math.min(max, n.intValue()));
        }
        try {
            return Math.max(min, Math.min(max, Integer.parseInt(String.valueOf(value))));
        } catch (Exception e) {
            return fallback;
        }
    }
}
