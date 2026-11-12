package com.zimo.gateway.common.log;

/**
 * 日志脱敏工具（v0.2 第 14 项）：token / 手机号 / 密钥等敏感字段打印前统一走这里，
 * 避免把凭证或个人信息原文落到日志文件。
 *
 * <pre>
 * token: eyJhbGciOiJIUzM4NCJ9.xxxxxx  →  eyJhbG...xxxx（前 6 + 后 4）
 * API Key: gwak-ab3k9xzm-9f2c...      →  gwak-a...c9d
 * 手机号: 13000000000                  →  130****0000
 * </pre>
 */
public final class LogMask {

    private LogMask() {
    }

    /** 通用长凭证：保留前 6 后 4，中间用 ... 代替；短值直接全遮 */
    public static String token(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        String v = value.trim();
        if (v.length() <= 12) {
            return "****";
        }
        return v.substring(0, 6) + "..." + v.substring(v.length() - 4);
    }

    /** 手机号：11 位保留前 3 后 4 */
    public static String phone(String value) {
        if (value == null || value.length() < 7) {
            return value;
        }
        return value.substring(0, 3) + "****" + value.substring(value.length() - 4);
    }

    /** 配置值脱敏：key 命中敏感词（password / secret / token / key）时整体遮罩 */
    public static String configValue(String configKey, String value) {
        if (value == null || configKey == null) {
            return value;
        }
        String k = configKey.toLowerCase();
        boolean sensitive = k.contains("password") || k.contains("secret")
                || k.contains("token") || k.contains("key");
        return sensitive ? token(value) : value;
    }

    /** 账号 / 名称类：保留首尾各 1 位 */
    public static String name(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        String v = value.trim();
        if (v.length() == 1) {
            return "*";
        }
        if (v.length() == 2) {
            return v.charAt(0) + "*";
        }
        return v.charAt(0) + "***" + v.charAt(v.length() - 1);
    }
}
