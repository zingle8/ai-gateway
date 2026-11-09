package com.zimo.gateway.proxy.ws;

import com.zimo.gateway.proxy.mapper.WsSessionMapper;

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
 * WebSocket 会话采集落库：内存队列 + 每秒批量写入，模式与 TraceSink 一致。
 *
 * <p>写库必须离开 Netty 事件循环线程；队列有界，满了丢弃并计数。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WsSessionSink {

    private static final int QUEUE_CAPACITY = 10_000;
    private static final int BATCH_SIZE = 200;

    private final WsSessionMapper mapper;

    private final BlockingQueue<WsSessionRecord> queue = new LinkedBlockingQueue<>(QUEUE_CAPACITY);
    private final AtomicLong dropped = new AtomicLong();
    private final AtomicLong saved = new AtomicLong();

    public void offer(WsSessionRecord record) {
        if (!queue.offer(record)) {
            long total = dropped.incrementAndGet();
            if (total % 100 == 1) {
                log.warn("[WS采集] 队列已满，累计丢弃 {} 条会话记录", total);
            }
        }
    }

    @Scheduled(fixedDelay = 1_000L, initialDelay = 2_000L)
    public void flush() {
        List<WsSessionRecord> batch = new ArrayList<>(BATCH_SIZE);
        queue.drainTo(batch, BATCH_SIZE);
        if (batch.isEmpty()) {
            return;
        }
        try {
            for (WsSessionRecord record : batch) {
                mapper.insert(record);
            }
            long total = saved.addAndGet(batch.size());
            if (total % 20 == 0 || batch.size() >= BATCH_SIZE) {
                log.debug("[WS采集] 累计落库 {} 条会话", total);
            }
        } catch (Exception e) {
            log.warn("[WS采集] 批量落库失败 {} 条: {}", batch.size(), e.getMessage());
        }
    }
}
