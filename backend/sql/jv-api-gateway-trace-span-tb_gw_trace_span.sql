-- =====================================================================
-- 调用链路（阶段一）：
--   1. 新建 tb_gw_trace_span —— 轻量 span 表（链路分析专用，与报文审计日志分离）
--   2. tb_gw_trace_log 改造为按天分区（原表无分区，日志量增长后 DELETE 成本极高）
--
-- 分区规范（两张表统一）：
--   - 按天 RANGE(TO_DAYS(...)) 分区，分区名 pYYYYMMDD；
--   - 分区键必须落在主键内：log 表 PK(id, req_time) / span 表 PK(id, start_time)；
--   - 由 jv-api-gateway-trace 的 PartitionMaintainer 每日维护：
--       滚动创建未来 3 天分区（缺则补），并删除 45 天前的历史分区（秒级 DROP）；
--   - 因此这里只预建「历史兜底 + 近 4 天」分区，其余交给维护任务。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. span 表（新表）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS tb_gw_trace_span (
  id             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  trace_id       VARCHAR(64)  NOT NULL                COMMENT '链路 ID（网关入口生成，全链路传播）',
  span_id        VARCHAR(32)  NOT NULL                COMMENT '当前 span ID（16 hex）',
  parent_span_id VARCHAR(32)          DEFAULT NULL    COMMENT '父 span ID（链路根节点为 NULL）',
  kind           VARCHAR(8)   NOT NULL                COMMENT '类型：SERVER=服务端处理 / CLIENT=发起调用',
  app_id         VARCHAR(64)          DEFAULT NULL    COMMENT '应用编码',
  app_name       VARCHAR(64)          DEFAULT NULL    COMMENT '应用名称',
  service_name   VARCHAR(64)  NOT NULL                COMMENT '当前服务名',
  api            VARCHAR(255)         DEFAULT NULL    COMMENT '接口，如 POST /api/v1/order/create',
  peer_service   VARCHAR(64)          DEFAULT NULL    COMMENT 'CLIENT span 的对端服务（服务拓扑聚合依据）',
  status_code    INT                  DEFAULT NULL    COMMENT '响应状态码（CLIENT 为对端返回码）',
  err_type       VARCHAR(32)          DEFAULT NULL    COMMENT '错误类型',
  cost           INT                  DEFAULT NULL    COMMENT '耗时（ms）',
  start_time     DATETIME(3)  NOT NULL                COMMENT 'span 开始时间（毫秒精度）',
  create_time    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '落库时间',
  PRIMARY KEY (id, start_time),
  KEY idx_trace  (trace_id)                          COMMENT '按链路聚合（瀑布图）',
  KEY idx_start  (start_time)                        COMMENT '时间窗查询 / 分区裁剪',
  KEY idx_edge   (service_name, peer_service, start_time) COMMENT '服务拓扑（调用对）聚合',
  KEY idx_span   (span_id)                           COMMENT '父 span 定位',
  KEY idx_time_trace_cost (start_time, trace_id, cost, status_code) COMMENT '慢链路聚合：覆盖索引免回表'
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '调用链路 span'
PARTITION BY RANGE (TO_DAYS(start_time)) (
  PARTITION p20261001 VALUES LESS THAN (TO_DAYS('2026-10-02')),
  PARTITION p20261103 VALUES LESS THAN (TO_DAYS('2026-11-04')),
  PARTITION p20261104 VALUES LESS THAN (TO_DAYS('2026-11-05')),
  PARTITION p20261105 VALUES LESS THAN (TO_DAYS('2026-11-06')),
  PARTITION p20261106 VALUES LESS THAN (TO_DAYS('2026-11-07')),
  PARTITION p20261107 VALUES LESS THAN (TO_DAYS('2026-11-08'))
);

-- ---------------------------------------------------------------------
-- 2. tb_gw_trace_log 改造为按天分区
--    要求：req_time NOT NULL 且并入主键（分区键必须在所有唯一键内）
-- ---------------------------------------------------------------------
UPDATE tb_gw_trace_log SET req_time = create_time WHERE req_time IS NULL;

-- 主键调整与分区必须分两步：一条 ALTER 里 MODIFY（仍是 PK 列）与 DROP PRIMARY KEY 混用时
-- 部分 MySQL 版本会报 1064。先改主键，再挂分区。
ALTER TABLE tb_gw_trace_log
  MODIFY req_time DATETIME NOT NULL COMMENT '请求时间（分区键）',
  DROP PRIMARY KEY,
  ADD PRIMARY KEY (id, req_time);

ALTER TABLE tb_gw_trace_log PARTITION BY RANGE (TO_DAYS(req_time)) (
    PARTITION p20261001 VALUES LESS THAN (TO_DAYS('2026-10-02')),
    PARTITION p20261103 VALUES LESS THAN (TO_DAYS('2026-11-04')),
    PARTITION p20261104 VALUES LESS THAN (TO_DAYS('2026-11-05')),
    PARTITION p20261105 VALUES LESS THAN (TO_DAYS('2026-11-06')),
    PARTITION p20261106 VALUES LESS THAN (TO_DAYS('2026-11-07')),
    PARTITION p20261107 VALUES LESS THAN (TO_DAYS('2026-11-08'))
  );

-- ---------------------------------------------------------------------
-- 3. 常用查询（索引对应关系备忘）
--    链路瀑布图 : idx_trace(trace_id) + idx_start 排序
--    服务拓扑   : idx_edge(service_name, peer_service, start_time)
--    时间窗统计 : idx_start（分区裁剪：WHERE start_time >= ? 直接定位天分区）
--    按 id 详情 : PK 前缀 id（全区扫描风险，详情仍走 tb_gw_trace_log 主键）
-- ---------------------------------------------------------------------
