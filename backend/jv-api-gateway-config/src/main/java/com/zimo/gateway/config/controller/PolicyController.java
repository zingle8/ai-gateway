package com.zimo.gateway.config.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zimo.gateway.common.exception.BizException;
import com.zimo.gateway.common.result.R;
import com.zimo.gateway.common.result.ResultCode;
import com.zimo.gateway.config.entity.SysConfigEntity;
import com.zimo.gateway.config.mapper.SysConfigMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 平台安全策略 / 登录方式配置（tb_sys_config JSON 存储）。
 *
 * <p>两个配置键：
 * <ul>
 *   <li>{@code platform.security.policy} —— 登录保护 / 验证码 / 密码策略 / MFA / 令牌有效期</li>
 *   <li>{@code platform.login.methods} —— 登录因子开关 + 邮件 / 短信通道配置</li>
 * </ul>
 * 登录流程实时读取（user 服务），策略修改立即生效，无需重启。
 */
@Slf4j
@RestController
@RequestMapping("/policies")
@RequiredArgsConstructor
public class PolicyController {

    public static final String POLICY_KEY = "platform.security.policy";
    public static final String METHODS_KEY = "platform.login.methods";

    private final SysConfigMapper mapper;
    private final ObjectMapper objectMapper;

    @GetMapping("/security")
    public R<Map<String, Object>> security() {
        return R.ok(read(POLICY_KEY));
    }

    @PutMapping("/security")
    public R<Map<String, Object>> saveSecurity(@RequestBody Map<String, Object> body) {
        return R.ok(write(POLICY_KEY, body, "安全策略"));
    }

    @GetMapping("/login-methods")
    public R<Map<String, Object>> loginMethods() {
        return R.ok(read(METHODS_KEY));
    }

    @PutMapping("/login-methods")
    public R<Map<String, Object>> saveLoginMethods(@RequestBody Map<String, Object> body) {
        return R.ok(write(METHODS_KEY, body, "登录方式"));
    }

    private Map<String, Object> read(String key) {
        SysConfigEntity entity = mapper.selectByKey(key);
        if (entity == null || entity.getConfigValue() == null || entity.getConfigValue().isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(entity.getConfigValue(), new TypeReference<>() {
            });
        } catch (Exception e) {
            log.warn("[策略] 配置 {} 解析失败，返回空：{}", key, e.getMessage());
            return Map.of();
        }
    }

    private Map<String, Object> write(String key, Map<String, Object> body, String label) {
        String json;
        try {
            json = objectMapper.writeValueAsString(body);
        } catch (Exception e) {
            throw new BizException(ResultCode.BAD_REQUEST.getCode(), label + "序列化失败");
        }
        if (body.isEmpty()) {
            throw new BizException(ResultCode.BAD_REQUEST.getCode(), label + "不能为空");
        }
        SysConfigEntity entity = mapper.selectByKey(key);
        if (entity == null) {
            entity = new SysConfigEntity();
            entity.setConfigKey(key);
            entity.setConfigValue(json);
            entity.setValueType("JSON");
            entity.setConfigSource("DB");
            entity.setPlatformName("API网关平台");
            entity.setAppName("jv-api-gateway");
            entity.setServiceName("*");
            entity.setRemark(label + "（控制台维护）");
            mapper.insert(entity);
        } else {
            mapper.updateValue(key, json);
        }
        log.info("[策略] {} 已更新（{} 字节）", label, json.length());
        return read(key);
    }
}
