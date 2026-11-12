package com.zimo.gateway.discovery.service;

import com.zimo.gateway.common.context.UserContext;
import com.zimo.gateway.discovery.entity.GwRouteEntity;
import com.zimo.gateway.discovery.mapper.ChangeLogMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 路由变更审计（v0.2 第 8 项）。
 *
 * <p>discovery 服务没有登录拦截器，操作人优先取 UserContext（若接入），
 * 其次取控制台携带的 {@code X-Username}，都没有则记 system；
 * 变更内容序列化为精简 JSON（只记影响转发的关键字段）。
 * 写审计失败不阻断路由修改。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RouteAuditWriter {

    private final ChangeLogMapper changeLogMapper;

    public void created(GwRouteEntity route) {
        record(route, "CREATE", null, snapshot(route));
    }

    public void updated(GwRouteEntity before, GwRouteEntity after) {
        record(after, "UPDATE", snapshot(before), snapshot(after));
    }

    public void deleted(GwRouteEntity before) {
        record(before, "DELETE", snapshot(before), null);
    }

    private void record(GwRouteEntity route, String action, String before, String after) {
        if (route == null) {
            return;
        }
        try {
            changeLogMapper.insert("ROUTE", route.getPathPrefix(), action, before, after,
                    operator(), UserContext.getUserId(), ip());
        } catch (Exception e) {
            log.warn("[审计] 路由变更留痕失败 {} {}：{}", action, route.getPathPrefix(), e.getMessage());
        }
    }

    /** 只记影响转发的字段，避免把 createTime 等噪音写进审计 */
    private String snapshot(GwRouteEntity route) {
        if (route == null) {
            return null;
        }
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("appId", route.getAppId());
        map.put("pathPrefix", route.getPathPrefix());
        map.put("upstreamUrl", route.getUpstreamUrl());
        map.put("stripPrefix", route.getStripPrefix());
        map.put("status", route.getStatus());
        StringBuilder sb = new StringBuilder("{");
        map.forEach((k, v) -> {
            if (sb.length() > 1) {
                sb.append(',');
            }
            sb.append('"').append(k).append("\":");
            if (v == null) {
                sb.append("null");
            } else if (v instanceof Number) {
                sb.append(v);
            } else {
                sb.append('"').append(v.toString().replace("\"", "\\\"")).append('"');
            }
        });
        return sb.append('}').toString();
    }

    private String operator() {
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

    private String ip() {
        HttpServletRequest request = currentRequest();
        if (request == null) {
            return null;
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        return forwarded != null && !forwarded.isBlank()
                ? forwarded.split(",")[0].trim() : request.getRemoteAddr();
    }

    private HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            return attrs.getRequest();
        }
        return null;
    }
}
