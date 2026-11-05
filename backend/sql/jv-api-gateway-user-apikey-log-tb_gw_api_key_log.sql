-- =============================================================================
-- jv-api-gateway-user | API Key 操作审计 | tb_gw_api_key_log
--
-- 记录签发 / 启停 / 轮换 / 吊销，供「API Key 统计看板」按今日维度汇总。
-- 主表只保留 Key 现状，历史动作全部落在这张追加表里（不做更新、不做删除）。
-- 由 PermSchemaInitializer 启动时自动执行（幂等）。
-- =============================================================================
USE api_gateway;

CREATE TABLE IF NOT EXISTS tb_gw_api_key_log (
  id               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  key_id           VARCHAR(32)  NOT NULL                COMMENT 'Key 短标识（gwak-<keyId>-<secret>）',
  key_name         VARCHAR(64)           DEFAULT NULL   COMMENT '操作时的 Key 名称快照',
  action           VARCHAR(16)  NOT NULL                COMMENT '动作：CREATE/ENABLE/DISABLE/ROTATE/DELETE',
  operator_user_id BIGINT               DEFAULT NULL   COMMENT '操作人用户 ID（开放接口/定时任务为 NULL）',
  operator_name    VARCHAR(64)           DEFAULT NULL   COMMENT '操作人账号快照',
  remark           VARCHAR(255)          DEFAULT NULL   COMMENT '备注',
  create_time      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发生时间',
  PRIMARY KEY (id),
  KEY idx_action_time (action, create_time),
  KEY idx_key (key_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='API Key 操作审计';
