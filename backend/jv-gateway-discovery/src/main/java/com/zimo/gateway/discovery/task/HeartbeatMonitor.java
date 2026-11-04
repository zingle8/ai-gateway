package com.zimo.gateway.discovery.task;

import com.zimo.gateway.discovery.mapper.GwServiceMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 心跳巡检：把长时间没上报心跳的服务标记为离线。
 *
 * <p>SDK 心跳间隔 30 秒，这里按 3 个周期（90 秒）判定超时。
 * 没有这个任务，服务进程被强杀后状态会永远停在「健康」。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HeartbeatMonitor {

    /** 超过该秒数没有心跳即视为离线 */
    private static final long TIMEOUT_SECONDS = 90L;

    private final GwServiceMapper serviceMapper;

    /**
     * 每 60 秒扫描一次。用 fixedDelay 而不是 fixedRate，
     * 避免上一轮慢查询时任务叠加。
     */
    @Scheduled(fixedDelay = 60_000L, initialDelay = 30_000L)
    public void markOffline() {
        LocalDateTime now = LocalDateTime.now();
        int affected = serviceMapper.markOffline("离线", now.minusSeconds(TIMEOUT_SECONDS), now);
        if (affected > 0) {
            log.info("[心跳巡检] {} 个服务超过 {} 秒无心跳，已标记为离线", affected, TIMEOUT_SECONDS);
        }
    }
}
