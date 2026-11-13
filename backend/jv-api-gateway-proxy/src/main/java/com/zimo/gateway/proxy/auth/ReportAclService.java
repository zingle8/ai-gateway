package com.zimo.gateway.proxy.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 上报通道访问控制（v0.2 多端/安全增强）：按 IP 与服务名（appId）对上报通道做黑白名单。
 *
 * <p>配置存 tb_sys_config（key = {@code platform.report.acl}，JSON）：
 * <pre>
 * {
 *   "enabled": true,                // 总开关
 *   "ipMode": "OFF",                // OFF 不启用 / BLACKLIST 黑名单 / WHITELIST 白名单
 *   "ipWhitelist": ["10.0.0.*"],    // 支持前缀通配（* 结尾）
 *   "ipBlacklist": ["203.0.113.7"],
 *   "serviceMode": "BLACKLIST",     // 对 X-Gateway-App-Id（appId / 服务名）同样三态
 *   "serviceWhitelist": ["order-service"],
 *   "serviceBlacklist": []
 * }
 * </pre>
 *
 * <p>10 秒缓存：控制台「上报通道管控」页修改后 10 秒内生效，无需重启网关。
 * 配置读取失败 / JSON 脏数据时 fail-open（不阻断上报），避免误配置打瘫采集链路。
 */
@Slf4j
@Service
public class ReportAclService {

    public static final String CONFIG_KEY = "platform.report.acl";
    public static final String HEADER_APP_ID = "X-Gateway-App-Id";

    private static final long CACHE_MILLIS = 10_000L;

    private final ReportAclMapper mapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private volatile Map<String, Object> cached;
    private volatile long loadedAt = 0;
    private volatile boolean loadFailed = false;

    public ReportAclService(ReportAclMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 校验上报请求是否放行。
     *
     * @return null = 放行；非 null = 拒绝原因（直接返回给调用方）
     */
    public String checkReport(String ip, String appId) {
        Map<String, Object> acl = config();
        if (acl == null || !Boolean.TRUE.equals(asBool(acl.get("enabled")))) {
            return null;
        }
        String ipMode = str(acl.get("ipMode"));
        if ("WHITELIST".equals(ipMode) && !matchAny(ip, list(acl.get("ipWhitelist")))) {
            return "上报来源 IP 不在白名单：" + ip;
        }
        if ("BLACKLIST".equals(ipMode) && matchAny(ip, list(acl.get("ipBlacklist")))) {
            return "上报来源 IP 已列入黑名单：" + ip;
        }
        String svcMode = str(acl.get("serviceMode"));
        boolean blankAppId = appId == null || appId.isBlank();
        if ("WHITELIST".equals(svcMode)) {
            // 白名单模式：未携带服务标识（旧版 SDK）同样视为不在名单 → 拒绝
            if (blankAppId) {
                return "上报服务未携带 X-Gateway-App-Id，白名单模式拒绝";
            }
            if (!matchAny(appId, list(acl.get("serviceWhitelist")))) {
                return "上报服务不在白名单：" + appId;
            }
        } else if ("BLACKLIST".equals(svcMode)) {
            // 黑名单模式：未携带标识无法比对，放行（由共享密钥兜底）
            if (!blankAppId && matchAny(appId, list(acl.get("serviceBlacklist")))) {
                return "上报服务已列入黑名单：" + appId;
            }
        }
        return null;
    }

    /** 读取配置（10 秒缓存；读取失败沿用上次配置，首次失败 fail-open） */
    @SuppressWarnings("unchecked")
    private Map<String, Object> config() {
        long now = System.currentTimeMillis();
        if (now - loadedAt < CACHE_MILLIS) {
            return cached;
        }
        synchronized (this) {
            if (now - loadedAt < CACHE_MILLIS) {
                return cached;
            }
            try {
                String raw = mapper.selectValue(CONFIG_KEY);
                Map<String, Object> parsed = raw == null || raw.isBlank()
                        ? Map.of() : objectMapper.readValue(raw, Map.class);
                cached = parsed;
                loadFailed = false;
            } catch (Exception e) {
                if (!loadFailed) {
                    log.warn("[上报ACL] 配置读取失败，沿用上次配置（fail-open）：{}", e.getMessage());
                    loadFailed = true;
                }
            }
            loadedAt = now;
            return cached;
        }
    }

    /** 前缀通配匹配：规则支持 "192.168.*" / 精确值 */
    static boolean matchAny(String value, List<String> rules) {
        if (value == null || value.isBlank() || rules.isEmpty()) {
            return false;
        }
        for (String rule : rules) {
            String r = rule == null ? "" : rule.trim();
            if (r.isEmpty()) {
                continue;
            }
            if (r.endsWith("*") ? value.startsWith(r.substring(0, r.length() - 1)) : r.equals(value)) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private static List<String> list(Object raw) {
        if (!(raw instanceof List<?> l)) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (Object item : l) {
            if (item != null && !String.valueOf(item).isBlank()) {
                out.add(String.valueOf(item).trim());
            }
        }
        return out;
    }

    private static Boolean asBool(Object raw) {
        if (raw instanceof Boolean b) {
            return b;
        }
        return raw != null && "true".equalsIgnoreCase(String.valueOf(raw));
    }

    private static String str(Object raw) {
        return raw == null ? "OFF" : String.valueOf(raw).trim().toUpperCase();
    }

    /** 配置样例（控制台页面展示用） */
    public static Map<String, Object> sample() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("enabled", false);
        m.put("ipMode", "OFF");
        m.put("ipWhitelist", new ArrayList<>());
        m.put("ipBlacklist", new ArrayList<>());
        m.put("serviceMode", "OFF");
        m.put("serviceWhitelist", new ArrayList<>());
        m.put("serviceBlacklist", new ArrayList<>());
        return m;
    }
}
