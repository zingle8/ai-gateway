package com.zimo.gateway.proxy.auth;

import lombok.extern.slf4j.Slf4j;

import java.net.InetAddress;

/**
 * 调用方 IP 白名单匹配（v0.2 第 6 项）。
 *
 * <p>支持三种写法，逗号分隔混用：</p>
 * <ul>
 *   <li>精确 IP：{@code 10.20.3.11}、{@code 127.0.0.1}</li>
 *   <li>CIDR 网段：{@code 10.20.0.0/16}（IPv4 / IPv6 均按位比较）</li>
 *   <li>前缀通配：{@code 10.20.3.*}（按字符串前缀，便于内网临时放开）</li>
 * </ul>
 *
 * <p>空白名单 = 不限制；来源 IP 解析不到（unknown）时按不通过处理（安全优先）。
 */
@Slf4j
public final class IpWhitelist {

    private IpWhitelist() {
    }

    public static boolean matches(String whitelist, String ip) {
        if (whitelist == null || whitelist.isBlank()) {
            return true;
        }
        String normalized = normalize(ip);
        if (normalized == null) {
            return false;
        }
        for (String raw : whitelist.split(",")) {
            String rule = raw.trim();
            if (rule.isEmpty()) {
                continue;
            }
            if ("*".equals(rule) || "0.0.0.0/0".equals(rule) || "::/0".equals(rule)) {
                return true;
            }
            if (rule.endsWith("*")) {
                if (normalized.startsWith(rule.substring(0, rule.length() - 1))) {
                    return true;
                }
            } else if (rule.contains("/")) {
                if (cidrMatch(rule, normalized)) {
                    return true;
                }
            } else if (rule.equals(normalized)) {
                return true;
            }
        }
        return false;
    }

    private static boolean cidrMatch(String rule, String ip) {
        int slash = rule.indexOf('/');
        String network = rule.substring(0, slash);
        int prefix;
        try {
            prefix = Integer.parseInt(rule.substring(slash + 1));
        } catch (NumberFormatException e) {
            return false;
        }
        try {
            byte[] addr = InetAddress.getByName(ip).getAddress();
            byte[] net = InetAddress.getByName(network).getAddress();
            if (addr.length != net.length || prefix < 0 || prefix > addr.length * 8) {
                return false;
            }
            int fullBytes = prefix / 8;
            for (int i = 0; i < fullBytes; i++) {
                if (addr[i] != net[i]) {
                    return false;
                }
            }
            int restBits = prefix % 8;
            if (restBits == 0) {
                return true;
            }
            int mask = 0xFF << (8 - restBits);
            return (addr[fullBytes] & mask) == (net[fullBytes] & mask);
        } catch (Exception e) {
            log.debug("[鉴权] IP 网段解析失败：rule={} ip={}，原因：{}", rule, ip, e.getMessage());
            return false;
        }
    }

    /** IPv6 映射地址归一到 IPv4；本机回环统一成 127.0.0.1，避免 ::1 与 127.0.0.1 判等失败 */
    static String normalize(String ip) {
        if (ip == null || ip.isBlank() || "unknown".equalsIgnoreCase(ip)) {
            return null;
        }
        String value = ip.trim();
        if (value.startsWith("::ffff:")) {
            value = value.substring("::ffff:".length());
        }
        if ("::1".equals(value) || "0:0:0:0:0:0:0:1".equals(value)) {
            return "127.0.0.1";
        }
        return value;
    }
}
