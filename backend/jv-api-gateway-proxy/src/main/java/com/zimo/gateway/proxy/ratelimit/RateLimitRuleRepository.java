package com.zimo.gateway.proxy.ratelimit;

import com.github.benmanes.caffeine.cache.Cache;
import com.zimo.gateway.proxy.mapper.RateLimitRuleMapper;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.List;

/**
 * 限流规则读取（5 秒本地缓存）。
 *
 * <p>控制台改规则最迟 5 秒生效；表不存在或查询失败时返回空列表（不阻断流量）。
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class RateLimitRuleRepository {

    private static final String CACHE_KEY = "enabled";

    private final RateLimitRuleMapper mapper;

    private final Cache<String, List<RateLimitRule>> cache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofSeconds(5))
            .maximumSize(1)
            .build();

    public List<RateLimitRule> enabledRules() {
        List<RateLimitRule> rules = cache.getIfPresent(CACHE_KEY);
        if (rules != null) {
            return rules;
        }
        try {
            rules = mapper.listEnabled();
        } catch (Exception e) {
            log.warn("[限流] 读取 tb_gw_rate_limit 失败，本次不启用任何限流规则：{}", e.getMessage());
            rules = List.of();
        }
        cache.put(CACHE_KEY, rules);
        return rules;
    }
}
