-- =============================================================================
-- jv-api-gateway-config | 变更审计 | tb_gw_change_log
--
-- 记录「谁在何时把什么改成了什么」：配置（含策略）与网关路由的增删改，
-- 复用 tb_gw_api_key_log 的审计表模式（操作人 / 动作 / 备注），
-- 额外保留改前值与改后值，供控制台「变更记录」查看。
--
-- biz_type：CONFIG（系统配置）/ POLICY（安全策略、登录方式、开放认证、日志级别）
--           ROUTE（网关路由）
-- action：  CREATE / UPDATE / DELETE
-- 由 PermSchemaInitializer 启动时自动执行（幂等）。
-- =============================================================================
USE api_gateway;

CREATE TABLE IF NOT EXISTS tb_gw_change_log (
  id           BIGINT AUTO_INCREMENT PRIMARY KEY,
  biz_type     VARCHAR(32)  NOT NULL COMMENT '业务类型：CONFIG / POLICY / ROUTE',
  biz_key      VARCHAR(191) NOT NULL COMMENT '业务主键：配置键 / 策略键 / 路由前缀',
  action       VARCHAR(16)  NOT NULL COMMENT 'CREATE / UPDATE / DELETE',
  before_value TEXT         NULL COMMENT '改前值（JSON 或原值，新增为空）',
  after_value  TEXT         NULL COMMENT '改后值（删除为空）',
  operator     VARCHAR(64)  NULL COMMENT '操作人',
  operator_id  BIGINT       NULL COMMENT '操作人 ID（有登录态时）',
  request_ip   VARCHAR(64)  NULL COMMENT '请求来源 IP',
  create_time  DATETIME DEFAULT CURRENT_TIMESTAMP,
  KEY idx_biz (biz_type, biz_key, create_time),
  KEY idx_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='配置与路由变更审计';
