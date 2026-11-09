-- =====================================================================
-- WebSocket 帧明细采集表 + 调度配置（v0.2 第 18 项增强：帧级日志）
-- 每条消息一行：方向（IN/OUT）、类型（TEXT/BINARY）、payload 样本（文本 UTF-8 /
-- 二进制 hex，前 N 字节）、原始长度、接收时间。session_key 关联会话表。
-- 采集配置（proxy）：gateway.trace.ws-frame-log（默认 true）、
--   ws-frame-sample-bytes（默认 1024）、ws-frame-max-per-session（默认 200）
-- 每日优化：tb_gw_ws_frame 按天分区（与 trace_log/span 同模式，PK 含分区列），
--   分区预建由调度中心 partition-daily 任务执行（提前 2 天）；
--   会话与帧表数据保留期清理由 data-clean 任务执行（默认 7 天，可配）
-- =====================================================================

-- 会话表补 session_key 列（帧明细关联）
ALTER TABLE tb_gw_ws_session
  ADD COLUMN session_key VARCHAR(64) DEFAULT NULL COMMENT '会话唯一键（UUID，帧明细关联）' AFTER id,
  ADD KEY idx_session_key (session_key);

CREATE TABLE IF NOT EXISTS tb_gw_ws_frame (
  id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  session_key VARCHAR(64)  DEFAULT NULL COMMENT '会话唯一键（tb_gw_ws_session.session_key）',
  trace_id    VARCHAR(64)  DEFAULT NULL COMMENT '链路追踪ID',
  direction   VARCHAR(4)   NOT NULL COMMENT '方向：IN=客户端→下游，OUT=下游→客户端',
  msg_type    VARCHAR(8)   NOT NULL COMMENT '帧类型：TEXT / BINARY',
  payload     TEXT         DEFAULT NULL COMMENT 'payload样本（文本UTF-8/二进制hex，前N字节）',
  length      INT          DEFAULT NULL COMMENT '原始payload字节数',
  recv_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '帧到达网关时间（分区列）',
  PRIMARY KEY (id, recv_time),
  KEY idx_session_key (session_key),
  KEY idx_recv_time (recv_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='WebSocket 帧明细采集（网关旁路捕获，按天分区）'
PARTITION BY RANGE (TO_DAYS(recv_time)) (
  PARTITION p20261109 VALUES LESS THAN (TO_DAYS('2026-11-10')),
  PARTITION p20261110 VALUES LESS THAN (TO_DAYS('2026-11-11'))
);

-- 纳入调度中心每日清理（保留 7 天）
INSERT INTO tb_scheduler_clean_table(table_name, time_column, keep_days, enabled, remark)
VALUES ('tb_gw_ws_session', 'conn_start', 7, 1, 'WebSocket 会话采集'),
       ('tb_gw_ws_frame', 'recv_time', 7, 1, 'WebSocket 帧明细采集（分区表）');

-- 纳入调度中心分区预建（提前 2 天）
INSERT INTO tb_scheduler_partition_config(table_name, partition_column, partition_type, advance_days, enabled)
VALUES ('tb_gw_ws_frame', 'recv_time', 'DAY', 2, 1);
