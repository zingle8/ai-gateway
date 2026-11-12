package com.zimo.gateway.config.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zimo.gateway.common.context.UserContext;
import com.zimo.gateway.common.log.LogMask;
import com.zimo.gateway.common.result.Page;
import com.zimo.gateway.config.entity.ChangeLogEntity;
import com.zimo.gateway.config.mapper.ChangeLogMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;
import java.util.Map;

/**
 * 变更审计（v0.2 第 8 项）：配置 / 策略 / 路由的增删改留痕。
 *
 * <p>记录「谁（operator + operatorId）、何时（create_time）、从什么改成什么
 * （before_value → after_value）、来源 IP」；敏感配置值写入前走
 * {@link LogMask#configValue(String, String)} 脱敏，避免审计表本身成为泄密点。
 *
 * <p>写审计失败不阻断业务（catch 后仅告警），保证配置修改可用性优先；
 * 但失败日志会打出来，便于发现表缺失等部署问题。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChangeLogService {

    private static final int MAX_VALUE_LENGTH = 4000;

    private final ChangeLogMapper mapper;
    private final ObjectMapper objectMapper;

    /** 记录一条变更（before / after 可为 null） */
    public void record(String bizType, String bizKey, String action, Object before, Object after) {
        try {
            ChangeLogEntity entity = new ChangeLogEntity();
            entity.setBizType(bizType);
            entity.setBizKey(bizKey);
            entity.setAction(action);
            entity.setBeforeValue(serialize(bizType, bizKey, before));
            entity.setAfterValue(serialize(bizType, bizKey, after));
            entity.setOperator(resolveOperator());
            entity.setOperatorId(UserContext.getUserId());
            entity.setRequestIp(resolveIp());
            mapper.insert(entity);
        } catch (Exception e) {
            log.warn("[审计] 变更留痕失败 {}/{} {}：{}", bizType, bizKey, action, e.getMessage());
        }
    }

    /** 变更记录查询（bizType / bizKey 可选），按时间倒序 */
    public Page<ChangeLogEntity> page(String bizType, String bizKey, int page, int size) {
        int current = Math.max(page, 1);
        int pageSize = Math.min(Math.max(size, 1), 200);
        List<ChangeLogEntity> records = mapper.selectPage(bizType, bizKey, (current - 1) * pageSize, pageSize);
        long total = mapper.count(bizType, bizKey);
        return Page.of(records, total, current, pageSize);
    }

    private String serialize(String bizType, String bizKey, Object value) {
        if (value == null) {
            return null;
        }
        String text = value instanceof String s ? s : toJson(value);
        if (text == null) {
            return null;
        }
        // 配置值里可能有密码 / 密钥，落审计表前先脱敏
        if ("CONFIG".equals(bizType) || bizKey.toLowerCase().contains("password")
                || bizKey.toLowerCase().contains("secret")) {
            text = LogMask.configValue(bizKey, text);
        }
        return text.length() > MAX_VALUE_LENGTH ? text.substring(0, MAX_VALUE_LENGTH) + "..." : text;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }

    private String resolveOperator() {
        String username = UserContext.getUsername();
        if (username != null && !username.isBlank()) {
            return username;
        }
        HttpServletRequest request = currentRequest();
        if (request != null) {
            String header = request.getHeader("X-Username");
            if (header != null && !header.isBlank()) {
                return header.trim();
            }
        }
        return "system";
    }

    private String resolveIp() {
        HttpServletRequest request = currentRequest();
        if (request == null) {
            return null;
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            return attrs.getRequest();
        }
        return null;
    }

    /** 便捷方法：策略类整体 JSON 变更 */
    public void recordPolicy(String policyKey, Map<String, Object> before, Map<String, Object> after) {
        record("POLICY", policyKey, before == null ? "CREATE" : "UPDATE", before, after);
    }
}
