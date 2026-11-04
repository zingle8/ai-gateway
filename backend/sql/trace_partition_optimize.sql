-- =====================================================================
-- 日志类表分区与索引优化（阶段二）：
--   1. tb_gw_service_edge（服务调用拓扑预聚合）纳入按天分区体系
--      —— 此前只有 tb_gw_trace_log / tb_gw_trace_span 分区，
--         edge 表长期只增不删（deleteBefore 除外），同样按天 RANGE 管理；
--   2. tb_gw_trace_log 索引与查询对齐：页面全部查询都是
--      「时间窗 + 应用/服务/错误类型」组合，单列索引过滤后仍需回表扫时间，
--      升级为 (前导列, req_time) 复合索引；
--   3. tb_gw_api 为接口登记配置表（百行级、无时间衰减），不做分区。
--
-- 分区规范与 trace_span.sql 阶段一保持一致：
--   - 按天 RANGE(TO_DAYS(...))，分区名 pYYYYMMDD，分区键在主键内；
--   - 日常滚动由 jv-api-gateway-trace 的 PartitionMaintainer 负责，
--     本脚本只负责结构改造 + 预建「历史兜底 + 近 4 天」分区。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. tb_gw_service_edge：主键并入分区键，改造为按天分区
--    （EdgeAggregator 全部按 window_start 增删查，不依赖单列 id 主键）
-- ---------------------------------------------------------------------
ALTER TABLE tb_gw_service_edge
    DROP PRIMARY KEY,
    ADD PRIMARY KEY (id, window_start);

ALTER TABLE tb_gw_service_edge
    PARTITION BY RANGE (TO_DAYS(window_start)) (
        PARTITION p20261001 VALUES LESS THAN (740256),  -- 历史兜底
        PARTITION p20261104 VALUES LESS THAN (740290),
        PARTITION p20261105 VALUES LESS THAN (740291),
        PARTITION p20261106 VALUES LESS THAN (740292),
        PARTITION p20261107 VALUES LESS THAN (740293)
    );

-- ---------------------------------------------------------------------
-- 2. tb_gw_trace_log：单列索引升级为「过滤列 + 时间」复合索引
--    （idx_reqtime / idx_trace 保持不变：分别服务纯时间窗分页与链路详情）
-- ---------------------------------------------------------------------
ALTER TABLE tb_gw_trace_log
    DROP KEY idx_service,   -- 单列服务名 → 服务 + 时间窗
    DROP KEY idx_errtype,   -- 错误类型基数低，单列过滤意义小 → 类型 + 时间窗
    ADD KEY idx_service_time (service_name, req_time),
    ADD KEY idx_app_time    (app_id, req_time),
    ADD KEY idx_errtype_time(err_type, req_time);
