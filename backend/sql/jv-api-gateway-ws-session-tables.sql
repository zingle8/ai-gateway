-- =====================================================================
-- WebSocket 会话采集表（jv-api-gateway-proxy）
-- 连接维度：双向消息数/字节数、双向首帧样本（前 N 字节）、起止时间与关闭状态。
-- 帧明细不落库；样本用于排查协议问题。trace_id 可关联 tb_gw_trace_log 的握手请求。
-- =====================================================================
CREATE TABLE IF NOT EXISTS tb_gw_ws_session (
  id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  trace_id      VARCHAR(64)  DEFAULT NULL COMMENT '链路追踪ID（握手请求的 X-Gateway-Trace-Id）',
  request_path  VARCHAR(500) DEFAULT NULL COMMENT '客户端握手请求地址',
  upstream_url  VARCHAR(500) DEFAULT NULL COMMENT '网关转发到的上游地址（ws://）',
  client_ip     VARCHAR(64)  DEFAULT NULL COMMENT '客户端IP',
  conn_start    DATETIME     DEFAULT NULL COMMENT '连接开始时间（握手完成）',
  conn_end      DATETIME     DEFAULT NULL COMMENT '连接结束时间',
  duration_ms   BIGINT       DEFAULT NULL COMMENT '连接时长（毫秒）',
  in_msg_count  INT          DEFAULT 0    COMMENT '入站消息数（客户端→下游）',
  in_bytes      BIGINT       DEFAULT 0    COMMENT '入站字节数',
  out_msg_count INT          DEFAULT 0    COMMENT '出站消息数（下游→客户端）',
  out_bytes     BIGINT       DEFAULT 0    COMMENT '出站字节数',
  in_sample     TEXT         DEFAULT NULL COMMENT '入站首帧样本（前 N 字节，二进制帧仅标记）',
  out_sample    TEXT         DEFAULT NULL COMMENT '出站首帧样本（前 N 字节）',
  close_status  VARCHAR(100) DEFAULT NULL COMMENT '关闭状态：NORMAL / ABNORMAL(信号)',
  create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '入库时间',
  PRIMARY KEY (id),
  KEY idx_trace_id (trace_id),
  KEY idx_conn_start (conn_start)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='WebSocket 会话采集（网关旁路捕获）';
