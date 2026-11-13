package com.zimo.gateway.user.service;

import com.zimo.gateway.common.exception.BizException;
import com.zimo.gateway.common.result.ResultCode;
import com.zimo.gateway.user.entity.IpBanEntity;
import com.zimo.gateway.user.mapper.IpBanMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * IP 封禁（v0.2 多端与安全增强）。
 *
 * <p>两级生效：
 * <ul>
 *   <li><b>登录拒绝</b>：user 服务登录入口先查 {@link #isBanned}，命中即 403 并登记 IP 风控事件；</li>
 *   <li><b>网关全量拦截</b>：封禁清单同步 Redis set {@code gw:ipban}，proxy 过滤器对每个请求
 *       SISMEMBER 命中即 403（Redis 不可用时网关侧 fail-open，仅登录侧仍拒绝）。</li>
 * </ul>
 *
 * <p>规则支持前缀通配（{@code 10.0.*}），匹配逻辑与 proxy 侧 {@code IpBanStore} 保持一致。
 */
@Slf4j
@Service
public class IpBanService {

    public static final String REDIS_SET = "gw:ipban";

    private final IpBanMapper mapper;
    private final ObjectProvider<StringRedisTemplate> redisProvider;

    public IpBanService(IpBanMapper mapper, ObjectProvider<StringRedisTemplate> redisProvider) {
        this.mapper = mapper;
        this.redisProvider = redisProvider;
    }

    /** 生效中的封禁清单 */
    public List<IpBanEntity> list() {
        return mapper.selectAll();
    }

    /** 手动 / 处置封禁一个 IP（幂等，重复封禁刷新原因与时间） */
    public IpBanEntity ban(String ip, String reason, String operator) {
        String normalized = normalize(ip);
        if (normalized == null) {
            throw new BizException(ResultCode.BAD_REQUEST.getCode(), "IP 不能为空");
        }
        IpBanEntity entity = new IpBanEntity();
        entity.setIp(normalized);
        entity.setReason(reason == null || reason.isBlank() ? "控制台手动封禁" : reason.trim());
        entity.setOperator(operator == null || operator.isBlank() ? "system" : operator);
        entity.setBannedAt(LocalDateTime.now());
        mapper.upsert(entity);
        syncRedisAdd(normalized);
        log.warn("[IP封禁] {} 已封禁（原因：{}，操作人：{}）", normalized, entity.getReason(), entity.getOperator());
        return mapper.selectByIp(normalized);
    }

    /** 解封（保留历史记录，status=0） */
    public int unban(String ip, String operator) {
        String normalized = normalize(ip);
        if (normalized == null) {
            throw new BizException(ResultCode.BAD_REQUEST.getCode(), "IP 不能为空");
        }
        int rows = mapper.unban(normalized, LocalDateTime.now());
        syncRedisRemove(normalized);
        log.info("[IP封禁] {} 已解封（操作人：{}）", normalized, operator == null ? "system" : operator);
        return rows;
    }

    /**
     * IP 是否被封禁（登录入口用）：Redis set 命中即拒；Redis 不可用查 DB 兜底。
     * 通配规则（10.0.*）无法用 set 精确判断，退化为逐条前缀比对（封禁量少，可接受）。
     */
    public boolean isBanned(String ip) {
        if (ip == null || ip.isBlank()) {
            return false;
        }
        StringRedisTemplate redis = redisProvider.getIfAvailable();
        if (redis != null) {
            try {
                if (Boolean.TRUE.equals(redis.opsForSet().isMember(REDIS_SET, ip))) {
                    return true;
                }
                // 通配规则走 DB 比对
                return mapper.selectAll().stream()
                        .filter(b -> b.getStatus() != null && b.getStatus() == 1)
                        .anyMatch(b -> matches(ip, b.getIp()));
            } catch (Exception e) {
                log.warn("[IP封禁] Redis 不可用，降级 DB 查询：{}", e.getMessage());
            }
        }
        return mapper.selectAll().stream()
                .filter(b -> b.getStatus() != null && b.getStatus() == 1)
                .anyMatch(b -> matches(ip, b.getIp()));
    }

    /** 前缀通配匹配：规则 "10.0.*" 命中 10.0.1.2 */
    public static boolean matches(String ip, String rule) {
        if (ip == null || rule == null || rule.isBlank()) {
            return false;
        }
        String r = rule.trim();
        return r.endsWith("*") ? ip.startsWith(r.substring(0, r.length() - 1)) : r.equals(ip.trim());
    }

    /** 启动 / 解封后的清单同步（封禁时增量 SADD，这里提供全量重建入口） */
    public void syncAllToRedis() {
        StringRedisTemplate redis = redisProvider.getIfAvailable();
        if (redis == null) {
            return;
        }
        try {
            redis.delete(REDIS_SET);
            List<String> ips = mapper.selectAll().stream()
                    .filter(b -> b.getStatus() != null && b.getStatus() == 1)
                    .filter(b -> !b.getIp().contains("*"))
                    .map(IpBanEntity::getIp)
                    .toList();
            if (!ips.isEmpty()) {
                redis.opsForSet().add(REDIS_SET, ips.toArray(new String[0]));
            }
            log.info("[IP封禁] 清单已同步 Redis：{} 条精确 IP", ips.size());
        } catch (Exception e) {
            log.warn("[IP封禁] Redis 同步失败：{}", e.getMessage());
        }
    }

    private void syncRedisAdd(String ip) {
        if (ip.contains("*")) {
            return; // 通配规则由 DB 比对兜底
        }
        StringRedisTemplate redis = redisProvider.getIfAvailable();
        if (redis != null) {
            try {
                redis.opsForSet().add(REDIS_SET, ip);
            } catch (Exception e) {
                log.warn("[IP封禁] Redis 同步失败：{}", e.getMessage());
            }
        }
    }

    private void syncRedisRemove(String ip) {
        StringRedisTemplate redis = redisProvider.getIfAvailable();
        if (redis != null) {
            try {
                redis.opsForSet().remove(REDIS_SET, ip);
            } catch (Exception e) {
                log.warn("[IP封禁] Redis 同步失败：{}", e.getMessage());
            }
        }
    }

    private static String normalize(String ip) {
        return ip == null || ip.isBlank() ? null : ip.trim();
    }
}
