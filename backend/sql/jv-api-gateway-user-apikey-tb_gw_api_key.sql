-- =============================================================================
-- jv-api-gateway-user | 开放令牌 | tb_gw_api_key
--
-- 网关 API Key：只存 SHA-256(secret)，明文仅签发/轮换时返回一次。
-- 由 PermSchemaInitializer 启动时自动执行（幂等）。
--
-- v0.2 第 6 项「开放认证调用方约束」补 4 列：
--   allowed_ips       调用方 IP 白名单（逗号分隔，支持精确 IP、10.20.3.* 前缀通配、* 表示不限）
--   allowed_services  可用服务白名单（tb_gw_route.service_name，逗号分隔，空 = 不限）
--   allowed_platforms 可用平台白名单（tb_gw_open_platform.app_id，逗号分隔，空 = 不限）
--   rate_limit_per_minute 该 Key 单独的每分钟调用上限（0 = 不单独限流，走 tb_gw_rate_limit 规则）
-- =============================================================================
USE api_gateway;

CREATE TABLE IF NOT EXISTS tb_gw_api_key (
  id                    BIGINT AUTO_INCREMENT PRIMARY KEY,
  key_id                VARCHAR(32)  NOT NULL,
  secret_hash           CHAR(64)     NOT NULL,
  name                  VARCHAR(64)  NOT NULL,
  owner_user_id         BIGINT       NULL,
  status                TINYINT      NOT NULL DEFAULT 1,
  expire_at             DATETIME     NULL,
  last_used_at          DATETIME     NULL,
  last_used_ip          VARCHAR(64)  NULL,
  allowed_ips           VARCHAR(512) NOT NULL DEFAULT '' COMMENT '调用方 IP 白名单，逗号分隔；空=不限',
  allowed_services      VARCHAR(512) NOT NULL DEFAULT '' COMMENT '可用服务白名单（service_name），空=不限',
  allowed_platforms     VARCHAR(512) NOT NULL DEFAULT '' COMMENT '可用平台白名单（app_id），空=不限',
  rate_limit_per_minute INT          NOT NULL DEFAULT 0  COMMENT '该 Key 每分钟调用上限，0=不单独限流',
  create_time           DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time           DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_key_id (key_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='网关 API Key';

-- 存量库补列：MySQL 5.7 / 8.x 均不支持 ADD COLUMN IF NOT EXISTS，
-- 用 information_schema 判断后动态执行，保证脚本可反复执行。
SET @ddl := (SELECT IF(COUNT(*) = 0,
  'ALTER TABLE tb_gw_api_key ADD COLUMN allowed_ips VARCHAR(512) NOT NULL DEFAULT '''' COMMENT ''调用方 IP 白名单，逗号分隔；空=不限'' AFTER last_used_ip',
  'SELECT 1') FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'tb_gw_api_key' AND COLUMN_NAME = 'allowed_ips');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl := (SELECT IF(COUNT(*) = 0,
  'ALTER TABLE tb_gw_api_key ADD COLUMN allowed_services VARCHAR(512) NOT NULL DEFAULT '''' COMMENT ''可用服务白名单（service_name），空=不限'' AFTER allowed_ips',
  'SELECT 1') FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'tb_gw_api_key' AND COLUMN_NAME = 'allowed_services');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl := (SELECT IF(COUNT(*) = 0,
  'ALTER TABLE tb_gw_api_key ADD COLUMN allowed_platforms VARCHAR(512) NOT NULL DEFAULT '''' COMMENT ''可用平台白名单（app_id），空=不限'' AFTER allowed_services',
  'SELECT 1') FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'tb_gw_api_key' AND COLUMN_NAME = 'allowed_platforms');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl := (SELECT IF(COUNT(*) = 0,
  'ALTER TABLE tb_gw_api_key ADD COLUMN rate_limit_per_minute INT NOT NULL DEFAULT 0 COMMENT ''该 Key 每分钟调用上限，0=不单独限流'' AFTER allowed_platforms',
  'SELECT 1') FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'tb_gw_api_key' AND COLUMN_NAME = 'rate_limit_per_minute');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
