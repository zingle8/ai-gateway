package com.zimo.gateway.discovery.controller;

import com.zimo.gateway.common.result.R;
import com.zimo.gateway.discovery.entity.HostMetricsEntity;
import com.zimo.gateway.discovery.mapper.HostMetricsMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 接入方主机运行指标（v0.2 第 10 项）。
 *
 * <ul>
 *   <li>{@code POST /discovery/metrics/batch} —— SDK 每 60 秒批量上报（单批 ≤ 500 点）；</li>
 *   <li>{@code GET /discovery/metrics/hosts} —— 窗口内每台主机的均值 / 峰值概览；</li>
 *   <li>{@code GET /discovery/metrics/trend} —— 单主机时间序列（按分钟归并）；</li>
 *   <li>{@code GET /discovery/metrics/hostnames} —— 趋势页主机下拉。</li>
 * </ul>
 *
 * <p>上报端点与 /discovery/register 同属开放通道（网关白名单 + 共享密钥头策略一致）；
 * 不做鉴权拦截，避免接入方为了上报指标必须引入登录态。
 */
@Slf4j
@RestController
@RequestMapping("/discovery/metrics")
@RequiredArgsConstructor
public class MetricsController {

    /** 单批上限：防止误传超大数组撑爆 SQL */
    private static final int MAX_BATCH = 500;
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("MM-dd HH:mm");

    private final HostMetricsMapper metricsMapper;

    /** 批量上报（幂等性由调用方保证：同一采集点重复上报会产生重复行） */
    @PostMapping("/batch")
    public R<Map<String, Object>> batch(@RequestBody List<HostMetricsEntity> batch) {
        if (batch == null || batch.isEmpty()) {
            return R.ok(Map.of("accepted", 0, "rejected", 0));
        }
        int rejected = 0;
        List<HostMetricsEntity> valid = new ArrayList<>();
        for (HostMetricsEntity item : batch) {
            if (item == null || !StringUtils.hasText(item.getHostname())) {
                rejected++;
                continue;
            }
            if (item.getReportTime() == null) {
                item.setReportTime(LocalDateTime.now());
            }
            if (item.getGpuCount() == null) {
                item.setGpuCount(0);
            }
            valid.add(item);
            if (valid.size() >= MAX_BATCH) {
                break;
            }
        }
        if (!valid.isEmpty()) {
            metricsMapper.insertBatch(valid);
        }
        if (log.isDebugEnabled()) {
            log.debug("[指标] 收到 {} 条主机指标，写入 {} 条，丢弃 {} 条", batch.size(), valid.size(), rejected);
        }
        return R.ok(Map.of("accepted", valid.size(), "rejected", rejected));
    }

    /** 主机资源概览（近 N 分钟） */
    @GetMapping("/hosts")
    public R<List<Map<String, Object>>> hosts(@RequestParam(defaultValue = "60") int minutes) {
        return R.ok(metricsMapper.selectHostOverview(clampMinutes(minutes)));
    }

    /**
     * 单主机趋势：按分钟归并（窗口 ≤ 3 小时用 1 分钟桶，更大的窗口用 5 分钟桶），
     * 返回 CPU / 内存 / 堆 / 磁盘 / 网络收发五组序列，供折线图直接消费。
     */
    @GetMapping("/trend")
    public R<Map<String, Object>> trend(@RequestParam String hostname,
                                        @RequestParam(defaultValue = "60") int minutes) {
        int window = clampMinutes(minutes);
        List<Map<String, Object>> rows = metricsMapper.selectTrend(hostname, window);
        long bucket = window <= 180 ? 1 : 5;

        Map<LocalDateTime, List<Map<String, Object>>> grouped = new java.util.TreeMap<>();
        for (Map<String, Object> row : rows) {
            Object raw = row.get("reportTime");
            LocalDateTime time;
            if (raw instanceof java.sql.Timestamp ts) {
                time = ts.toLocalDateTime();
            } else if (raw instanceof LocalDateTime ldt) {
                time = ldt;
            } else {
                continue;
            }
            // 按分钟（或 5 分钟）对齐分桶，标签直接用本地时间，避免时区偏移
            LocalDateTime slot = time.truncatedTo(java.time.temporal.ChronoUnit.MINUTES);
            if (bucket > 1) {
                slot = slot.withMinute((int) (slot.getMinute() / bucket * bucket));
            }
            grouped.computeIfAbsent(slot, k -> new ArrayList<>()).add(row);
        }

        List<String> times = new ArrayList<>();
        List<Double> cpu = new ArrayList<>();
        List<Double> mem = new ArrayList<>();
        List<Double> heap = new ArrayList<>();
        List<Double> disk = new ArrayList<>();
        List<Long> rx = new ArrayList<>();
        List<Long> tx = new ArrayList<>();
        grouped.forEach((slot, points) -> {
            times.add(slot.format(FMT));
            cpu.add(avg(points, "cpuUsage"));
            mem.add(avg(points, "memUsedPct"));
            heap.add(avg(points, "jvmHeapPct"));
            disk.add(avg(points, "diskUsedPct"));
            rx.add(Math.round(avg(points, "netRxBps")));
            tx.add(Math.round(avg(points, "netTxBps")));
        });

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("hostname", hostname);
        result.put("minutes", window);
        result.put("bucketMinutes", bucket);
        result.put("times", times);
        result.put("cpu", cpu);
        result.put("mem", mem);
        result.put("heap", heap);
        result.put("disk", disk);
        result.put("rx", rx);
        result.put("tx", tx);
        return R.ok(result);
    }

    /** 趋势页主机下拉（近 24 小时有上报的主机） */
    @GetMapping("/hostnames")
    public R<List<String>> hostnames(@RequestParam(defaultValue = "1440") int minutes) {
        return R.ok(metricsMapper.selectHostnames(clampMinutes(minutes)));
    }

    private int clampMinutes(int minutes) {
        return Math.min(Math.max(minutes, 5), 10080);
    }

    private double avg(List<Map<String, Object>> points, String key) {
        double sum = 0;
        int count = 0;
        for (Map<String, Object> point : points) {
            Object value = point.get(key);
            if (value instanceof Number number) {
                sum += number.doubleValue();
                count++;
            }
        }
        return count == 0 ? 0 : Math.round(sum / count * 100) / 100.0;
    }
}
