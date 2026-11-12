package com.zimo.gateway.proxy.auth;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.zimo.gateway.proxy.mapper.AuthExemptMapper;
import com.zimo.gateway.proxy.ratelimit.AuthExemptRule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import org.springframework.util.AntPathMatcher;

import java.time.Duration;
import java.util.List;

/**
 * 鉴权 / 限流豁免规则（5 秒缓存）。
 *
 * <p>语义是「追加放行」：yml 内置白名单始终有效，本表只额外放行。
 * 表不存在或查询失败时返回空列表（不影响内置白名单）。
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class AuthExemptRepository {

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();
    private static final String CACHE_KEY = "effective";

    private final AuthExemptMapper mapper;

    private final Cache<String, List<AuthExemptRule>> cache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofSeconds(5))
            .maximumSize(1)
            .build();

    /**
     * 是否命中豁免规则。
     *
     * @param need LOGIN 免登录 / RATE 免限流；ALL 类型对两者都生效
     */
    public boolean isExempt(String path, String method, String need) {
        List<AuthExemptRule> rules = rules();
        if (rules.isEmpty()) {
            return false;
        }
        String httpMethod = method == null ? "GET" : method.toUpperCase();
        for (AuthExemptRule rule : rules) {
            String type = rule.getExemptType() == null ? "" : rule.getExemptType().toUpperCase();
            boolean typeHit = "ALL".equals(type) || need.equals(type);
            if (!typeHit) {
                continue;
            }
            if (!methodHit(rule.getMethods(), httpMethod)) {
                continue;
            }
            if (PATH_MATCHER.match(rule.getPathPattern(), path)) {
                return true;
            }
        }
        return false;
    }

    private boolean methodHit(String methods, String httpMethod) {
        if (methods == null || methods.isBlank() || "*".equals(methods.trim())) {
            return true;
        }
        for (String item : methods.split(",")) {
            if (httpMethod.equals(item.trim().toUpperCase())) {
                return true;
            }
        }
        return false;
    }

    private List<AuthExemptRule> rules() {
        List<AuthExemptRule> cached = cache.getIfPresent(CACHE_KEY);
        if (cached != null) {
            return cached;
        }
        List<AuthExemptRule> rules;
        try {
            rules = mapper.listEffective();
        } catch (Exception e) {
            log.warn("[豁免] 读取 tb_gw_auth_exempt 失败，仅使用内置白名单：{}", e.getMessage());
            rules = List.of();
        }
        cache.put(CACHE_KEY, rules);
        return rules;
    }
}
