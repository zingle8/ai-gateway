package com.zimo.gateway.common.util;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 全平台统一的时间统计维度（与前端 @zimo/shared 的 TIME_RANGES 一一对应）。
 *
 * <p>十个档位覆盖「秒级实时排障 → 月度趋势」，每个档位同时约定：
 * <ul>
 *   <li>{@code code}：前后端交互用的区间标识（1m / 5m / ... / 30d）</li>
 *   <li>{@code windowSeconds}：时间窗跨度，过滤条件为 {@code time >= since()}</li>
 *   <li>{@code bucketSeconds}：趋势图桶宽，区间内固定切 {@link #POINTS} 个点</li>
 * </ul>
 *
 * <p>24 小时与 1 天跨度相同但语义不同：前者是滚动 24 小时，后者是自然日（今日 00:00 起），
 * 由 {@link #since()} 区分，避免两个选项结果一模一样。
 */
public enum TimeRange {

    M1("1m", "1分钟", 60, 1),
    M5("5m", "5分钟", 300, 5),
    M10("10m", "10分钟", 600, 10),
    M30("30m", "30分钟", 1800, 30),
    H1("1h", "1小时", 3600, 60),
    H2("2h", "2小时", 7200, 120),
    H24("24h", "24小时", 86400, 1440),
    D1("1d", "1天", 86400, 1440),
    D7("7d", "7天", 604800, 10080),
    D30("30d", "30天", 2592000, 43200);

    /** 趋势图固定点数：各档位桶宽 = windowSeconds / POINTS */
    public static final int POINTS = 60;

    public static final TimeRange DEFAULT = H24;

    private final String code;
    private final String label;
    private final long windowSeconds;
    private final int bucketSeconds;

    TimeRange(String code, String label, long windowSeconds, int bucketSeconds) {
        this.code = code;
        this.label = label;
        this.windowSeconds = windowSeconds;
        this.bucketSeconds = bucketSeconds;
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    /** 时间窗跨度（秒） */
    public long getWindowSeconds() {
        return windowSeconds;
    }

    /** 时间窗跨度（分钟），给按分钟过滤的 SQL 用 */
    public long getWindowMinutes() {
        return Math.max(1, windowSeconds / 60);
    }

    /** 趋势图桶宽（秒） */
    public int getBucketSeconds() {
        return bucketSeconds;
    }

    /** 区间文案，如「近 24 小时」 */
    public String getRangeLabel() {
        return "近 " + label;
    }

    /** 时间窗起点：1天 取自然日 00:00，其余按 now - windowSeconds 滚动 */
    public LocalDateTime since() {
        if (this == D1) {
            return LocalDate.now().atStartOfDay();
        }
        return LocalDateTime.now().minusSeconds(windowSeconds);
    }

    /** 按 code 解析；空值或非法值兜底 24 小时，不抛异常打断页面 */
    public static TimeRange of(String code) {
        if (code == null || code.isBlank()) {
            return DEFAULT;
        }
        for (TimeRange r : values()) {
            if (r.code.equalsIgnoreCase(code.trim())) {
                return r;
            }
        }
        return DEFAULT;
    }
}
