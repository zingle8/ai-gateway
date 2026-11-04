package com.zimo.gateway.trace.task;

import com.zimo.gateway.trace.mapper.GwServiceEdgeMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * 服务拓扑预聚合任务（阶段二）。
 *
 * <p>每分钟对「最近 3 个整点小时窗口」从 tb_gw_trace_span 的 CLIENT span
 * 全量重算并 UPSERT 覆盖 tb_gw_service_edge：
 * <ul>
 *   <li>重算而非增量累加 —— 对同一窗口重复执行结果幂等，span 迟到落库也会在
 *       下一轮被纠正（窗口覆盖 3 小时，足够容纳批量上报的延迟）；</li>
 *   <li>拓扑图 / 依赖分析只查这张小表，不在 span 大表上做实时 GROUP BY；</li>
 *   <li>窗口数据保留 {@link #RETENTION_DAYS} 天，任务内顺带清理。</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EdgeAggregator {

    /** 每轮重算的小时窗口数（含当前不完整窗口） */
    private static final int REBUILD_WINDOWS = 3;

    /** 聚合结果保留天数 */
    private static final int RETENTION_DAYS = 30;

    private final GwServiceEdgeMapper edgeMapper;

    @Scheduled(fixedDelay = 60_000L, initialDelay = 15_000L)
    public void aggregate() {
        try {
            // 当前小时窗口起点；窗口 [ws, ws+1h)
            LocalDateTime current = LocalDateTime.now().truncatedTo(ChronoUnit.HOURS);
            int edges = 0;
            for (int i = 0; i < REBUILD_WINDOWS; i++) {
                LocalDateTime ws = current.minusHours(i);
                edges += edgeMapper.rebuildWindow(ws);
            }
            int cleaned = edgeMapper.deleteBefore(current.minusDays(RETENTION_DAYS));
            if (edges > 0 || cleaned > 0) {
                log.info("[拓扑] 聚合完成：重算 {} 条边窗口，清理 {} 条过期", edges, cleaned);
            }
        } catch (Exception e) {
            log.error("[拓扑] 聚合失败：{}", e.getMessage());
        }
    }
}
