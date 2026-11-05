package com.zimo.gateway.user.model;

import java.util.Map;

/**
 * API Key 统计看板
 *
 * @param total           Key 总数
 * @param enabled         启用中
 * @param disabled        已停用
 * @param todayCreated    今日新增（签发）
 * @param todayRotated    今日轮换（更换密钥）
 * @param todayRenewed    今日续期（延长有效期）
 * @param todayRevoked    今日吊销（停用）
 * @param todayDeleted    今日删除
 * @param todayEnabled    今日启用
 * @param expiringIn7Days 7 日内到期
 */
public record ApiKeyStatsVO(long total, long enabled, long disabled,
                            long todayCreated, long todayRotated, long todayRenewed, long todayRevoked,
                            long todayDeleted, long todayEnabled, long expiringIn7Days) {

    public static ApiKeyStatsVO from(Map<String, Object> row) {
        return new ApiKeyStatsVO(
                num(row, "total"), num(row, "enabled"), num(row, "disabled"),
                num(row, "todayCreated"), num(row, "todayRotated"), num(row, "todayRenewed"),
                num(row, "todayRevoked"), num(row, "todayDeleted"), num(row, "todayEnabled"),
                num(row, "expiringIn7Days"));
    }

    private static long num(Map<String, Object> row, String key) {
        Object v = row == null ? null : row.get(key);
        return v instanceof Number n ? n.longValue() : 0L;
    }
}
