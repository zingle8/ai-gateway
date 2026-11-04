package com.zimo.gateway.client.web;

import com.zimo.gateway.client.common.constant.GatewayClientConstant;

/**
 * traceparent（W3C Trace Context）解析与生成，与网关侧 TraceContextUtil 保持同一格式：
 * {@code 00-<traceId:32hex>-<spanId:16hex>-01}。
 */
public final class TraceContextUtil {

    private TraceContextUtil() {
    }

    /** 解析结果：traceId + 上游 spanId（作为本服务 SERVER span 的 parent） */
    public record Parsed(String traceId, String spanId) {
    }

    public static Parsed parse(String traceparent) {
        if (traceparent == null) {
            return null;
        }
        String[] parts = traceparent.trim().split("-");
        if (parts.length != 4 || !"00".equals(parts[0])
                || parts[1].length() != 32 || parts[2].length() != 16
                || !isHex(parts[1]) || !isHex(parts[2])) {
            return null;
        }
        return new Parsed(parts[1], parts[2]);
    }

    public static String format(String traceId, String spanId) {
        return "00-" + traceId + "-" + spanId + "-01";
    }

    /** 16 hex 的 span ID；traceId 沿用 32 hex 无连字符 UUID 格式 */
    public static String newSpanId() {
        return java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    /** 从旧版 X-Gateway-Trace-Id 兜底（只有 traceId，无父 span） */
    public static Parsed fromLegacy(String traceId) {
        if (traceId == null || traceId.isEmpty()) {
            return null;
        }
        return new Parsed(traceId, null);
    }

    private static boolean isHex(String s) {
        for (char c : s.toCharArray()) {
            if (Character.digit(c, 16) < 0) {
                return false;
            }
        }
        return true;
    }
}
