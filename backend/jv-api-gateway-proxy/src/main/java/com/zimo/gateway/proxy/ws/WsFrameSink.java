package com.zimo.gateway.proxy.ws;

import com.zimo.gateway.proxy.mapper.WsFrameMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * WebSocket 帧明细采集落库：内存队列 + 每秒批量写入，模式与 TraceSink / WsSessionSink 一致。
 *
 * <p>帧量与消息量成正比，队列有界（满即丢并计数），批量落库失败仅告警不阻塞转发。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WsFrameSink {

    private static final int QUEUE_CAPACITY = 20_000;
    private static final int BATCH_SIZE = 300;

    private final WsFrameMapper mapper;

    private final BlockingQueue<WsFrameRecord> queue = new LinkedBlockingQueue<>(QUEUE_CAPACITY);
    private final AtomicLong dropped = new AtomicLong();
    private final AtomicLong saved = new AtomicLong();

    public void offer(WsFrameRecord record) {
        if (!queue.offer(record)) {
            long total = dropped.incrementAndGet();
            if (total % 200 == 1) {
                log.warn("[WS帧采集] 队列已满，累计丢弃 {} 条帧记录", total);
            }
        }
    }

    @Scheduled(fixedDelay = 1_000L, initialDelay = 2_000L)
    public void flush() {
        List<WsFrameRecord> batch = new ArrayList<>(BATCH_SIZE);
        queue.drainTo(batch, BATCH_SIZE);
        if (batch.isEmpty()) {
            return;
        }
        try {
            for (WsFrameRecord record : batch) {
                mapper.insert(record);
            }
            saved.addAndGet(batch.size());
        } catch (Exception e) {
            log.warn("[WS帧采集] 批量落库失败 {} 条: {}", batch.size(), e.getMessage());
        }
    }
}
