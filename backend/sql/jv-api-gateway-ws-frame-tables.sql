-- =====================================================================
-- WebSocket 帧明细采集表（v0.2 第 18 项增强：帧级日志）
-- 每条消息一行：方向（IN/OUT）、类型（TEXT/BINARY）、payload 样本（文本 UTF-8 /
-- 二进制 hex，前 N 字节）、原始长度、接收时间。session_key 关联会话表。
-- 配置（proxy）：gateway.trace.ws-frame-log（默认 true）、
--   ws-frame-sample-bytes（默认 1024）、ws-frame-max-per-session（默认 200）
-- =====================================================================
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
  recv_time   DATETIME     DEFAULT NULL COMMENT '帧到达网关时间',
  PRIMARY KEY (id),
  KEY idx_session_key (session_key),
  KEY idx_recv_time (recv_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='WebSocket 帧明细采集（网关旁路捕获）';
