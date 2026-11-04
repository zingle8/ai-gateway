package com.zimo.gateway.common.util;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 获取客户端真实 IP（考虑网关 / 代理转发头）
 */
public final class IpUtil {

    private static final String[] HEADERS = {
            "X-Forwarded-For",
            "X-Real-IP",
            "Proxy-Client-IP",
            "WL-Proxy-Client-IP"
    };

    private IpUtil() {
    }

    public static String get(HttpServletRequest request) {
        if (request == null) {
            return "unknown";
        }
        for (String header : HEADERS) {
            String value = request.getHeader(header);
            if (value != null && !value.isEmpty() && !"unknown".equalsIgnoreCase(value)) {
                return value.split(",")[0].trim();
            }
        }
        return request.getRemoteAddr();
    }
}
