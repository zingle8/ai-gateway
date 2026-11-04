package com.zimo.gateway.trace.task;

import com.zimo.gateway.trace.mapper.GwTraceSpanMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 日志/链路表按天分区维护。
 *
 * <p>三张表（tb_gw_trace_log / tb_gw_trace_span / tb_gw_service_edge）统一按天
 * RANGE(TO_DAYS) 分区：
 * <ul>
 *   <li>补建未来 {@link #PRE_CREATE_DAYS} 天分区 —— 保证任何时刻写入都有落点，
 *       否则插入会报 "Table has no partition for value" 直接丢数据；</li>
 *   <li>删除 {@link #RETENTION_DAYS} 天前的历史分区 —— DROP PARTITION 是秒级元数据操作，
 *       等价于旧方案「定时 DELETE + 逻辑删除」的替代，物理释放空间且不产生 binlog 峰值。</li>
 * </ul>
 *
 * <p>启动即执行一次（{@link ApplicationRunner}），之后每天 00:05 滚动；
 * DDL 是库级的，平台内服务单实例调度，无并发冲突。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PartitionMaintainer implements ApplicationRunner {

    /** 需要维护的分区表（trace_log 调用日志 / trace_span 链路 / service_edge 拓扑预聚合） */
    private static final List<String> TABLES = List.of("tb_gw_trace_log", "tb_gw_trace_span", "tb_gw_service_edge");

    /** 预建未来分区数：连续 3 天没起服务也不丢写入 */
    private static final int PRE_CREATE_DAYS = 3;

    /** 日志保留天数（分区粒度） */
    private static final int RETENTION_DAYS = 45;

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final GwTraceSpanMapper mapper;

    @Override
    public void run(ApplicationArguments args) {
        maintain();
    }

    @Scheduled(cron = "0 5 0 * * ?")
    public void daily() {
        maintain();
    }

    void maintain() {
        LocalDate today = LocalDate.now();
        for (String table : TABLES) {
            try {
                maintainTable(table, today);
            } catch (Exception e) {
                // 维护失败不影响业务：最多是预建分区滞后，次日重试
                log.error("[分区] {} 维护失败：{}", table, e.getMessage());
            }
        }
    }

    private void maintainTable(String table, LocalDate today) {
        List<Map<String, Object>> partitions = mapper.selectPartitions(table);
        Set<String> names = partitions.stream()
                .map(p -> String.valueOf(p.get("name")))
                .collect(Collectors.toSet());
        // description 是该分区上界 TO_DAYS 值，数值化便于比较
        Set<Long> bounds = partitions.stream()
                .map(p -> Long.parseLong(String.valueOf(p.get("description"))))
                .collect(Collectors.toSet());

        int added = 0;
        // 从「今天」起预建未来 PRE_CREATE_DAYS 天；已存在则跳过
        for (int i = 0; i <= PRE_CREATE_DAYS; i++) {
            LocalDate day = today.plusDays(i);
            // 分区 pYYYYMMDD 存「< 次日」的数据：上界取 day+1
            LocalDate upper = day.plusDays(1);
            String name = "p" + day.format(DAY);
            if (names.contains(name)) {
                continue;
            }
            mapper.addPartition(table, name, toDays(upper));
            added++;
        }

        long retentionBound = toDays(today.minusDays(RETENTION_DAYS));
        int dropped = 0;
        for (Long bound : bounds) {
            if (bound < retentionBound) {
                String name = partitions.stream()
                        .filter(p -> Long.parseLong(String.valueOf(p.get("description"))) == bound)
                        .map(p -> String.valueOf(p.get("name")))
                        .findFirst().orElse(null);
                if (name != null && !"p20261001".equals(name)) { // 历史兜底分区保留，防止旧数据无处安放
                    mapper.dropPartition(table, name);
                    dropped++;
                }
            }
        }
        log.info("[分区] {} 维护完成：补建 {} 个，清理 {} 个，当前 {} 个分区", table, added, dropped, partitions.size() + added - dropped);
    }

    private int toDays(LocalDate date) {
        return (int) date.toEpochDay() + 719528; // LocalDate epochDay → MySQL TO_DAYS
    }
}
