-- =============================================================================
-- jv-api-gateway-user | 账号平台授权 | tb_gw_open_platform / tb_gw_account_platform
--
-- 开放平台登记（第三方业务平台）+ 账号-平台授权关系。
-- 「认证了 ≠ 能用」：开放认证换 token 时若带 appId，校验该账号是否被授权访问该平台。
-- 由 PermSchemaInitializer 启动时自动执行（幂等）。
-- =============================================================================
USE api_gateway;

-- 第三方业务平台（接入方）登记表
CREATE TABLE IF NOT EXISTS tb_gw_open_platform (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  app_id      VARCHAR(64)  NOT NULL,
  app_name    VARCHAR(128) NOT NULL,
  status      TINYINT      NOT NULL DEFAULT 1,
  remark      VARCHAR(255) NULL,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_app_id (app_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='开放平台（接入方）登记';

-- 账号-平台授权关系（一个账号可授权到 N 个平台）
CREATE TABLE IF NOT EXISTS tb_gw_account_platform (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id     BIGINT      NOT NULL,
  app_id      VARCHAR(64) NOT NULL,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_user_app (user_id, app_id),
  KEY idx_app_id (app_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='账号-开放平台授权关系';

-- 种子数据：演示用接入方（存在则跳过）
INSERT INTO tb_gw_open_platform (app_id, app_name, remark)
SELECT 'zimo-erp', '齐墨ERP', '企业资源计划系统（演示平台）'
WHERE NOT EXISTS (SELECT 1 FROM tb_gw_open_platform WHERE app_id = 'zimo-erp');

INSERT INTO tb_gw_open_platform (app_id, app_name, remark)
SELECT 'data-center', '数据中台', '统一数据查询与分析（演示平台）'
WHERE NOT EXISTS (SELECT 1 FROM tb_gw_open_platform WHERE app_id = 'data-center');

INSERT INTO tb_gw_open_platform (app_id, app_name, remark)
SELECT 'bi-report', '报表达人', '自助式报表平台（演示平台）'
WHERE NOT EXISTS (SELECT 1 FROM tb_gw_open_platform WHERE app_id = 'bi-report');
