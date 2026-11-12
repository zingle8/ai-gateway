-- =============================================================================
-- jv-api-gateway-discovery | 部署关联 | tb_gw_service.hostname
--
-- v0.2 第 11 项：服务实例 ↔ 主机/pod 关联。K8s 下 hostname 即 pod name、
-- ip 即 pod IP；非容器环境 hostname 为主机名。SDK 注册时上报。
-- 存量库用 information_schema 判断后动态补列，脚本可反复执行（MySQL 5.7）。
-- 由 PermSchemaInitializer 启动时自动执行。
-- =============================================================================
USE api_gateway;

SET @ddl := (SELECT IF(COUNT(*) = 0,
  'ALTER TABLE tb_gw_service ADD COLUMN hostname VARCHAR(128) NULL COMMENT ''主机名 / pod name，SDK 注册时上报'' AFTER ip',
  'SELECT 1') FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'tb_gw_service' AND COLUMN_NAME = 'hostname');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 平台自身模块补 hostname（示例：注册时 SDK 已带主机名；历史行为空，前端按 IP 兜底显示）
UPDATE tb_gw_service
   SET hostname = CONCAT('localhost-', port)
 WHERE hostname IS NULL
   AND ip IN ('127.0.0.1', 'localhost')
   AND app_id = 'app-api-gateway';
