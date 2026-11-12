-- =============================================================================
-- jv-api-gateway-discovery | 接入方主机运行指标 | tb_gw_host_metrics
--
-- v0.2 第 10 项：SDK 每 60 秒采集一次主机 / 进程指标并批量上报，
-- 本表按天 RANGE 分区（分区列 report_time，TO_DAYS 口径，与 tb_gw_trace_span 一致；
-- MySQL 5.7 不允许 UNIX_TIMESTAMP(列) 这种时区相关表达式做分区键）。
--
-- 指标覆盖六大类：CPU / GPU（可选）/ 内存与 JVM / 磁盘 / 网络 / IO；
-- 无 GPU 环境下 gpu_count = 0、gpu_util / gpu_mem_pct 为 NULL。
-- 由 PermSchemaInitializer 启动时自动执行（幂等）。
-- =============================================================================
USE api_gateway;

CREATE TABLE IF NOT EXISTS tb_gw_host_metrics (
  id             BIGINT AUTO_INCREMENT,
  hostname       VARCHAR(128) NOT NULL COMMENT '主机名（K8s 下即 pod name）',
  app_id         VARCHAR(64)  NULL COMMENT '归属应用',
  service_name   VARCHAR(64)  NULL COMMENT '上报服务名',
  os_name        VARCHAR(32)  NULL,
  os_version     VARCHAR(64)  NULL,
  cpu_cores      INT          NULL COMMENT 'CPU 核数',
  cpu_usage      DECIMAL(5,2) NULL COMMENT '系统 CPU 使用率 %',
  load_avg       DECIMAL(6,2) NULL COMMENT '1 分钟平均负载',
  mem_total      BIGINT       NULL COMMENT '内存总量（字节）',
  mem_used_pct   DECIMAL(5,2) NULL COMMENT '系统内存使用率 %',
  proc_rss       BIGINT       NULL COMMENT '进程 RSS（字节）',
  jvm_heap_pct   DECIMAL(5,2) NULL COMMENT 'JVM 堆使用率 %',
  gc_count       BIGINT       NULL COMMENT 'GC 次数累计',
  gc_time_ms     BIGINT       NULL COMMENT 'GC 耗时累计（毫秒）',
  thread_count   INT          NULL COMMENT '线程数',
  fd_count       INT          NULL COMMENT '文件描述符数',
  disk_total     BIGINT       NULL COMMENT '磁盘总量（字节）',
  disk_used_pct  DECIMAL(5,2) NULL COMMENT '磁盘使用率 %',
  disk_read_bps  BIGINT       NULL COMMENT '磁盘读吞吐（B/s）',
  disk_write_bps BIGINT       NULL COMMENT '磁盘写吞吐（B/s）',
  disk_iops      INT          NULL COMMENT '磁盘 IOPS（读写合计）',
  net_rx_bps     BIGINT       NULL COMMENT '网卡入流量（B/s）',
  net_tx_bps     BIGINT       NULL COMMENT '网卡出流量（B/s）',
  net_conn       INT          NULL COMMENT 'TCP 已建立连接数',
  gpu_count      INT          NOT NULL DEFAULT 0 COMMENT 'GPU 数量，0 表示无 GPU',
  gpu_util       DECIMAL(5,2) NULL COMMENT 'GPU 利用率 %',
  gpu_mem_pct    DECIMAL(5,2) NULL COMMENT '显存使用率 %',
  report_time    DATETIME     NOT NULL COMMENT '采集时间',
  create_time    DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id, report_time),
  KEY idx_host_time (hostname, report_time),
  KEY idx_time (report_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='接入方主机运行指标'
PARTITION BY RANGE (TO_DAYS(report_time)) (
  PARTITION p20261112 VALUES LESS THAN (TO_DAYS('2026-11-13')),
  PARTITION p20261113 VALUES LESS THAN (TO_DAYS('2026-11-14')),
  PARTITION p20261114 VALUES LESS THAN (TO_DAYS('2026-11-15')),
  PARTITION p_max VALUES LESS THAN MAXVALUE
);

-- 说明：调度中心「分区预建」当前生成的是 UNIX_TIMESTAMP 口径的 ADD PARTITION 语句，
-- 与本表 TO_DAYS 分区不匹配，故暂不登记；本期依赖 p_max 兜底（写入不受影响），
-- 分区预建待调度中心支持 TO_DAYS 口径后再接入。
