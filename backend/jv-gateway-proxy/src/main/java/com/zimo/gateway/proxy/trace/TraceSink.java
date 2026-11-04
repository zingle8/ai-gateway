package com.zimo.gateway.proxy.trace;

import com.zimo.gateway.proxy.mapper.TraceLogMapper;
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
 * 调用记录落库：内存队列 + 每秒批量写入（原生 MyBatis）。
 *
 * <p>写库必须离开 Netty 事件循环线程，否则会阻塞转发。
 * 队列有界，满了直接丢弃并计数，宁可丢日志也不能拖慢业务请求。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TraceSink {

    private static final int QUEUE_CAPACITY = 10_000;
    private static final int BATCH_SIZE = 200;

    private final TraceLogMapper traceLogMapper;

    private final BlockingQueue<TraceRecord> queue = new LinkedBlockingQueue<>(QUEUE_CAPACITY);
    private final AtomicLong dropped = new AtomicLong();
    private final AtomicLong saved = new AtomicLong();

    /** 非阻塞投递，队列满即丢 */
    public void offer(TraceRecord record) {
        if (!queue.offer(record)) {
            long total = dropped.incrementAndGet();
            if (total % 100 == 1) {
                log.warn("[采集] 队列已满，累计丢弃 {} 条调用记录", total);
            }
        }
    }

    @Scheduled(fixedDelay = 1_000L, initialDelay = 2_000L)
    public void flush() {
        List<TraceRecord> batch = new ArrayList<>(BATCH_SIZE);
        queue.drainTo(batch, BATCH_SIZE);
        if (batch.isEmpty()) {
            return;
        }
        try {
            traceLogMapper.batchInsert(batch);
            saved.addAndGet(batch.size());
        } catch (Exception e) {
            log.error("[采集] 批量写入失败，本批 {} 条被丢弃：{}", batch.size(), e.getMessage());
        }
    }

    public long savedCount() {
        return saved.get();
    }

    public long droppedCount() {
        return dropped.get();
    }
}
