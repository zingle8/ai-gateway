-- ============================================================
-- 阶段二：服务调用拓扑（预聚合表 + 慢链路查询）
-- 依赖：jv-api-gateway-trace-span-tb_gw_trace_span.sql（tb_gw_trace_span 必须先建）
--
-- 设计：
--   EdgeAggregator（jv-api-gateway-trace）每分钟对「最近 3 个整点小时窗口」
--   从 tb_gw_trace_span 的 CLIENT span 全量重算并 UPSERT 覆盖本表，
--   幂等可重跑；拓扑图 / 依赖分析只查这张小表，不做 span 大表实时 GROUP BY。
--   窗口数据保留 30 天，任务内顺带清理。
-- ============================================================

CREATE TABLE IF NOT EXISTS tb_gw_service_edge (
  id           BIGINT       NOT NULL AUTO_INCREMENT,
  src_service  VARCHAR(64)  NOT NULL COMMENT '调用方（CLIENT span 的 service_name）',
  dst_service  VARCHAR(64)  NOT NULL COMMENT '被调方（CLIENT span 的 peer_service）',
  window_start DATETIME     NOT NULL COMMENT '聚合窗口起点（整点）',
  calls        INT          NOT NULL DEFAULT 0 COMMENT '调用次数',
  errors       INT          NOT NULL DEFAULT 0 COMMENT '失败次数（status>=400）',
  total_cost   BIGINT       NOT NULL DEFAULT 0 COMMENT '总耗时 ms（avg=total_cost/calls）',
  max_cost     INT          NOT NULL DEFAULT 0 COMMENT '最大耗时 ms',
  update_time  DATETIME     NOT NULL COMMENT '本次聚合时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_edge_window (src_service, dst_service, window_start),
  KEY idx_window (window_start)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='服务调用拓扑预聚合（按小时窗口，EdgeAggregator 维护）';
