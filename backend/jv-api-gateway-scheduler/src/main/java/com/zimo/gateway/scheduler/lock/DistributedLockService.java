package com.zimo.gateway.scheduler.lock;

import com.zimo.gateway.common.constant.GwRedisKeys;
import com.zimo.gateway.scheduler.mapper.SchedulerMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Date;
import java.util.UUID;

/**
 * 分布式锁：Redis 第一优先级（SET NX EX），MySQL 锁表兜底。
 *
 * 双存储策略：Redis 可用时用 Redis 锁（高性能、自动过期）；
 * Redis 不可用自动降级 MySQL 锁表（INSERT IGNORE 抢占 + 过期重抢），
 * 保证多实例部署下任务不被重复执行，且不因 Redis 故障而停摆。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DistributedLockService {

    private final StringRedisTemplate redis;
    private final SchedulerMapper mapper;

    /**
     * 尝试获取分布式锁
     *
     * @param lockKey    锁键（如 job:data-clean）
     * @param ttlSeconds 持锁上限（秒），到期自动释放防死锁
     * @return 锁持有者标识（供解锁使用），获取失败返回 null
     */
    public String tryLock(String lockKey, int ttlSeconds) {
        String holder = UUID.randomUUID().toString();
        // ① Redis 优先
        try {
            Boolean ok = redis.opsForValue()
                    .setIfAbsent(GwRedisKeys.SCHEDULER_LOCK_PREFIX + lockKey, holder, Duration.ofSeconds(ttlSeconds));
            if (Boolean.TRUE.equals(ok)) {
                return holder;
            }
            return null;
        } catch (Exception e) {
            log.warn("Redis 锁不可用，降级 MySQL 锁表: {}", e.getMessage());
        }
        // ② MySQL 兜底：先清过期锁，再抢占
        try {
            mapper.releaseOrExpireLock(lockKey, holder);
            Date expireAt = new Date(System.currentTimeMillis() + ttlSeconds * 1000L);
            return mapper.tryLockMysql(lockKey, holder, expireAt) == 1 ? holder : null;
        } catch (Exception e) {
            log.error("MySQL 锁表抢占失败: {}", e.getMessage());
            return null;
        }
    }

    /** 释放锁（校验持有者，防误删他人锁） */
    public void unlock(String lockKey, String holder) {
        if (holder == null) {
            return;
        }
        try {
            String v = redis.opsForValue().get(GwRedisKeys.SCHEDULER_LOCK_PREFIX + lockKey);
            if (holder.equals(v)) {
                redis.delete(GwRedisKeys.SCHEDULER_LOCK_PREFIX + lockKey);
            }
        } catch (Exception e) {
            log.warn("Redis 解锁失败，走 MySQL: {}", e.getMessage());
        }
        try {
            mapper.unlockMysql(lockKey, holder);
        } catch (Exception e) {
            log.error("MySQL 解锁失败: {}", e.getMessage());
        }
    }
}
