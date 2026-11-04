package com.zimo.gateway.client.report;

import com.zimo.gateway.client.common.constant.GatewayClientConstant;
import com.zimo.gateway.client.common.dto.TraceLogDTO;
import com.zimo.gateway.client.properties.GatewayClientProperties;
import com.zimo.gateway.client.support.RestClientFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 调用日志上报器：内存队列 + 单线程异步批量上报。
 *
 * 设计要点：
 * 1. 采集只做 offer，队列满直接丢弃，绝不阻塞业务线程；
 * 2. 定时刷写 + JVM 关闭钩子兜底，尽量不丢尾部日志；
 * 3. 上报失败只记 debug 日志，绝不影响业务。
 */
@Slf4j
public class TraceReporter implements DisposableBean {

    private final RestClient restClient;

    private final BlockingQueue<TraceLogDTO> queue =
            new ArrayBlockingQueue<>(GatewayClientConstant.DEFAULT_QUEUE_SIZE);

    private final ScheduledExecutorService executor;

    private final int batchSize;

    private volatile boolean closed = false;

    public TraceReporter(GatewayClientProperties properties) {
        this.batchSize = Math.max(1, properties.getTrace().getBatchSize());
        this.restClient = RestClientFactory.create(
                normalize(properties.getServerAddr()), properties.getTrace().getReportTimeout());
        this.executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "zimo-gateway-trace-reporter");
            thread.setDaemon(true);
            return thread;
        });
        long interval = Math.max(500L, properties.getTrace().getFlushInterval());
        this.executor.scheduleWithFixedDelay(this::flushQuietly, interval, interval, TimeUnit.MILLISECONDS);
    }

    /**
     * 采集一条调用日志
     */
    public void offer(TraceLogDTO log) {
        if (closed || log == null) {
            return;
        }
        queue.offer(log);
    }

    /**
     * 立即刷写（应用关闭或运维触发时使用）
     */
    public int flush() {
        int total = 0;
        while (!queue.isEmpty()) {
            List<TraceLogDTO> batch = new ArrayList<>(batchSize);
            queue.drainTo(batch, batchSize);
            if (batch.isEmpty()) {
                break;
            }
            try {
                restClient.post()
                        .uri(GatewayClientConstant.PATH_TRACE_BATCH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(batch)
                        .retrieve()
                        .toBodilessEntity();
                total += batch.size();
            } catch (Exception e) {
                log.debug("上报网关调用日志失败，丢弃 {} 条：{}", batch.size(), e.getMessage());
                break;
            }
        }
        return total;
    }

    private void flushQuietly() {
        try {
            flush();
        } catch (Exception e) {
            log.debug("定时刷写调用日志异常：{}", e.getMessage());
        }
    }

    @Override
    public void destroy() {
        closed = true;
        executor.shutdownNow();
        flushQuietly();
    }

    private static String normalize(String serverAddr) {
        if (serverAddr == null || serverAddr.isEmpty()) {
            return GatewayClientConstant.DEFAULT_SERVER_ADDR;
        }
        return serverAddr.endsWith("/") ? serverAddr.substring(0, serverAddr.length() - 1) : serverAddr;
    }
}
