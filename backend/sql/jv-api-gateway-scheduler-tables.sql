-- ============================================================
-- 分布式调度中心（jv-api-gateway-scheduler）数据表
-- 模块端口：28087；网关路由：/api/scheduler/** -> http://127.0.0.1:28087
-- ============================================================

-- 1) 调度任务定义
CREATE TABLE IF NOT EXISTS tb_scheduler_job (
  id            INT AUTO_INCREMENT PRIMARY KEY,
  job_key       VARCHAR(64)  NOT NULL COMMENT '任务键：data-clean / partition-daily',
  job_name      VARCHAR(128) NOT NULL COMMENT '任务名称',
  schedule_type VARCHAR(16)  NOT NULL DEFAULT 'DAILY_TIME' COMMENT '调度类型：DAILY_TIME',
  run_time      VARCHAR(8)   NOT NULL DEFAULT '02:00' COMMENT '每日执行时间 HH:mm',
  status        TINYINT      NOT NULL DEFAULT 1 COMMENT '1 启用 0 停用',
  last_run_time DATETIME              DEFAULT NULL COMMENT '上次执行时间',
  last_run_status VARCHAR(16)          DEFAULT NULL COMMENT '上次执行状态 SUCCESS/FAILED',
  remark        VARCHAR(255)          DEFAULT NULL,
  create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_job_key (job_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='调度任务定义';

-- 2) 分布式锁（MySQL 兜底：Redis 不可用时降级使用）
CREATE TABLE IF NOT EXISTS tb_scheduler_lock (
  lock_key    VARCHAR(64) PRIMARY KEY,
  holder      VARCHAR(64) NOT NULL COMMENT '持有者标识（UUID）',
  expire_at   DATETIME    NOT NULL COMMENT '过期时间（防死锁）',
  create_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='调度分布式锁（MySQL 兜底）';

-- 3) 执行计划（分区预建等前瞻性计划记录）
CREATE TABLE IF NOT EXISTS tb_scheduler_plan (
  id              BIGINT AUTO_INCREMENT PRIMARY KEY,
  job_key         VARCHAR(64)  NOT NULL,
  plan_type       VARCHAR(32)  NOT NULL COMMENT 'ADD_PARTITION 等',
  plan_date       DATE         NOT NULL COMMENT '计划执行日期',
  target          VARCHAR(64)  NOT NULL COMMENT '作用对象：表名',
  partition_name  VARCHAR(32)           DEFAULT NULL COMMENT '分区名 p20261110',
  partition_value VARCHAR(32)           DEFAULT NULL COMMENT '分区边界值',
  status          VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/DONE/FAILED',
  detail          VARCHAR(512)          DEFAULT NULL,
  create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  done_time       DATETIME              DEFAULT NULL,
  KEY idx_plan_job (job_key, plan_date),
  KEY idx_plan_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='调度执行计划';

-- 4) 执行记录（每次触发的实际结果）
CREATE TABLE IF NOT EXISTS tb_scheduler_run_log (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  job_key     VARCHAR(64)  NOT NULL,
  run_type    VARCHAR(16)  NOT NULL DEFAULT 'SCHEDULED' COMMENT 'SCHEDULED/MANUAL',
  target      VARCHAR(64)           DEFAULT NULL COMMENT '作用对象：表名/分区名',
  start_time  DATETIME     NOT NULL,
  end_time    DATETIME              DEFAULT NULL,
  status      VARCHAR(16)  NOT NULL COMMENT 'SUCCESS/FAILED',
  total_rows  BIGINT                DEFAULT 0 COMMENT '处理行数',
  detail      VARCHAR(2048)         DEFAULT NULL COMMENT '结果摘要',
  error       VARCHAR(1024)         DEFAULT NULL COMMENT '失败原因',
  host        VARCHAR(64)           DEFAULT NULL COMMENT '执行实例 host:port',
  create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_run_job (job_key, start_time),
  KEY idx_run_status_day (job_key, status, start_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='调度执行记录';

-- 5) 清理清单配置（新增采集表只需加一行）
CREATE TABLE IF NOT EXISTS tb_scheduler_clean_table (
  id          INT AUTO_INCREMENT PRIMARY KEY,
  table_name  VARCHAR(64) NOT NULL,
  time_column VARCHAR(64) NOT NULL COMMENT '时间归属列',
  keep_days   INT         NOT NULL DEFAULT 7 COMMENT '保留天数：删除该天数之前数据',
  enabled     TINYINT     NOT NULL DEFAULT 1,
  remark      VARCHAR(255)         DEFAULT NULL,
  UNIQUE KEY uk_table (table_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='历史数据清理清单';

-- 6) 分区表配置
CREATE TABLE IF NOT EXISTS tb_scheduler_partition_config (
  id               INT AUTO_INCREMENT PRIMARY KEY,
  table_name       VARCHAR(64) NOT NULL,
  partition_column VARCHAR(64) NOT NULL COMMENT 'RANGE UNIX_TIMESTAMP 分区列',
  partition_type   VARCHAR(8)  NOT NULL DEFAULT 'DAY' COMMENT '分区粒度',
  advance_days     INT         NOT NULL DEFAULT 2 COMMENT '提前预建天数',
  enabled          TINYINT     NOT NULL DEFAULT 1,
  UNIQUE KEY uk_ptable (table_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='分区表配置';

-- ============ 初始数据 ============

INSERT INTO tb_scheduler_job(job_key, job_name, schedule_type, run_time, status, remark) VALUES
('data-clean',      '历史数据清理（物理删除 7 天外采集日志/记录表）', 'DAILY_TIME', '02:00', 1, '执行时间=系统设置页「调度时间」配置项，Redis/MySQL 双存储'),
('partition-daily', '分区表每日分区预建（提前 2 天）',               'DAILY_TIME', '02:10', 1, '跟随清理时间 +10 分钟执行')
ON DUPLICATE KEY UPDATE job_name = VALUES(job_name);

INSERT INTO tb_scheduler_clean_table(table_name, time_column, keep_days, enabled, remark) VALUES
('tb_gw_trace_log',    'req_time',   7, 1, '网关请求日志（分区表）'),
('tb_gw_trace_span',   'start_time', 7, 1, '调用链 span（分区表）'),
('tb_gw_api_key_log',  'create_time',7, 1, 'API Key 调用审计'),
('tb_gw_service_edge', 'window_start',7, 1, '服务拓扑边聚合（分区表）')
ON DUPLICATE KEY UPDATE time_column = VALUES(time_column);

INSERT INTO tb_scheduler_partition_config(table_name, partition_column, partition_type, advance_days, enabled) VALUES
('tb_gw_trace_log',  'req_time',   'DAY', 2, 1),
('tb_gw_trace_span', 'start_time', 'DAY', 2, 1)
ON DUPLICATE KEY UPDATE partition_column = VALUES(partition_column);

-- 调度时间配置项（系统设置页读写；tb_sys_config 有唯一键时生效）
INSERT IGNORE INTO tb_sys_config(config_key, config_value, remark) VALUES
('scheduler.clean.run-time', '02:00', '历史数据清理任务每日执行时间（HH:mm）');

-- 网关路由（平台内部服务）
INSERT INTO tb_gw_route(app_id, app_name, service_name, path_prefix, upstream_url, strip_prefix, status, remark) VALUES
('app-api-gateway', 'API网关平台', 'jv-api-gateway-scheduler', '/api/scheduler/**', 'http://127.0.0.1:28087', 0, 1, '平台自身：分布式调度中心')
ON DUPLICATE KEY UPDATE upstream_url = VALUES(upstream_url), update_time = NOW();
