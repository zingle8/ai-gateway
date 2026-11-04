package com.zimo.gateway.proxy.trace;

/**
 * W3C Trace Context（traceparent）解析工具。
 *
 * <p>格式：{@code 00-<trace_id:32hex>-<span_id:16hex>-<flags:2hex>}。
 * trace_id 与现有 {@code X-Gateway-Trace-Id} 的 32 位无连字符 UUID 格式一致，
 * 因此 header 同时传播两种格式：旧 SDK 认 X-Gateway-Trace-Id，新链路认 traceparent。
 */
public final class TraceContextUtil {

    private TraceContextUtil() {
    }

    /** traceparent 的版本前缀（当前固定 00） */
    private static final String VERSION = "00";

    /**
     * 解析结果：traceId + 上游 spanId（作为网关 SERVER span 的 parent）
     */
    public record Parsed(String traceId, String spanId) {
    }

    /** 解析 traceparent；格式非法返回 null（此时调用方应生成新链路） */
    public static Parsed parse(String traceparent) {
        if (traceparent == null) {
            return null;
        }
        String[] parts = traceparent.trim().split("-");
        if (parts.length != 4 || !VERSION.equals(parts[0])
                || parts[1].length() != 32 || parts[2].length() != 16
                || !isHex(parts[1]) || !isHex(parts[2])) {
            return null;
        }
        return new Parsed(parts[1], parts[2]);
    }

    /** 组装 traceparent；spanId 为「即将发给下游的 CLIENT span」 */
    public static String format(String traceId, String spanId) {
        return VERSION + "-" + traceId + "-" + spanId + "-01";
    }

    /** 生成 16 hex 的 span ID（traceId 沿用既有 32 hex 无连字符 UUID 格式） */
    public static String newSpanId() {
        return java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 16);
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
