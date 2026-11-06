-- =============================================================================
-- jv-api-gateway-proxy | 路由转发 | tb_gw_route
--
-- 网关转发路由：业务方在控制台「路由配置」维护，proxy 每 30 秒热加载。
-- 由 ProxySchemaInitializer 启动时自动执行（幂等）。
-- =============================================================================
USE api_gateway;

CREATE TABLE IF NOT EXISTS tb_gw_route (
  id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  app_id        VARCHAR(64)  NOT NULL                COMMENT '归属应用编码',
  app_name      VARCHAR(64)           DEFAULT NULL   COMMENT '归属应用名称',
  service_name  VARCHAR(64)           DEFAULT NULL   COMMENT '命中的服务ID（英文唯一标识）',
  path_prefix   VARCHAR(200) NOT NULL                COMMENT '匹配路径，如 /order/**',
  upstream_url  VARCHAR(255) NOT NULL                COMMENT '转发目标，如 http://10.20.3.11:8080',
  prefix_path   VARCHAR(100)          DEFAULT NULL   COMMENT '转发前追加的路径前缀，如 /api',
  strip_prefix  TINYINT               DEFAULT 0      COMMENT '转发时是否去掉第一段路径：1=去掉',
  status        TINYINT               DEFAULT 1      COMMENT '1=启用 0=停用',
  remark        VARCHAR(255)          DEFAULT NULL   COMMENT '备注',
  create_time   DATETIME              DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time   DATETIME              DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted       TINYINT               DEFAULT 0      COMMENT '逻辑删除：0=正常 1=删除',
  PRIMARY KEY (id),
  KEY idx_prefix (path_prefix),
  KEY idx_app (app_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='网关转发路由';

-- ============================== 种子：平台自身路由 ==============================
INSERT INTO tb_gw_route (app_id, app_name, service_name, path_prefix, upstream_url, prefix_path, strip_prefix, status, remark)
SELECT 'app-api_gateway', 'API网关平台', 'jv-api-gateway-discovery', '/gw-demo/**', 'http://127.0.0.1:28083', '/api', 1, 1, '自测路由：转发到服务发现接口'
WHERE NOT EXISTS (SELECT 1 FROM tb_gw_route WHERE path_prefix = '/gw-demo/**');

INSERT INTO tb_gw_route (app_id, app_name, service_name, path_prefix, upstream_url, prefix_path, strip_prefix, status, remark)
SELECT 'app-api_gateway', 'API网关平台', 'jv-api-gateway-user', '/api/auth/**', 'http://127.0.0.1:28081', NULL, 0, 1, '平台自身：认证服务（含 /open/auth 开放认证）'
WHERE NOT EXISTS (SELECT 1 FROM tb_gw_route WHERE path_prefix = '/api/auth/**');

INSERT INTO tb_gw_route (app_id, app_name, service_name, path_prefix, upstream_url, prefix_path, strip_prefix, status, remark)
SELECT 'app-api_gateway', 'API网关平台', 'jv-api-gateway-user', '/api/users/**', 'http://127.0.0.1:28081', NULL, 0, 1, '平台自身：用户管理'
WHERE NOT EXISTS (SELECT 1 FROM tb_gw_route WHERE path_prefix = '/api/users/**');

INSERT INTO tb_gw_route (app_id, app_name, service_name, path_prefix, upstream_url, prefix_path, strip_prefix, status, remark)
SELECT 'app-api_gateway', 'API网关平台', 'jv-api-gateway-user', '/api/perm/**', 'http://127.0.0.1:28081', NULL, 0, 1, '平台自身：分组与权限'
WHERE NOT EXISTS (SELECT 1 FROM tb_gw_route WHERE path_prefix = '/api/perm/**');

INSERT INTO tb_gw_route (app_id, app_name, service_name, path_prefix, upstream_url, prefix_path, strip_prefix, status, remark)
SELECT 'app-api_gateway', 'API网关平台', 'jv-api-gateway-user', '/api/keys/**', 'http://127.0.0.1:28081', NULL, 0, 1, '平台自身：API Key 管理'
WHERE NOT EXISTS (SELECT 1 FROM tb_gw_route WHERE path_prefix = '/api/keys/**');

INSERT INTO tb_gw_route (app_id, app_name, service_name, path_prefix, upstream_url, prefix_path, strip_prefix, status, remark)
SELECT 'app-api_gateway', 'API网关平台', 'jv-api-gateway-overview', '/api/overview/**', 'http://127.0.0.1:28082', NULL, 0, 1, '平台自身：总览统计'
WHERE NOT EXISTS (SELECT 1 FROM tb_gw_route WHERE path_prefix = '/api/overview/**');

INSERT INTO tb_gw_route (app_id, app_name, service_name, path_prefix, upstream_url, prefix_path, strip_prefix, status, remark)
SELECT 'app-api_gateway', 'API网关平台', 'jv-api-gateway-overview', '/api/stats/**', 'http://127.0.0.1:28082', NULL, 0, 1, '平台自身：数据统计'
WHERE NOT EXISTS (SELECT 1 FROM tb_gw_route WHERE path_prefix = '/api/stats/**');

INSERT INTO tb_gw_route (app_id, app_name, service_name, path_prefix, upstream_url, prefix_path, strip_prefix, status, remark)
SELECT 'app-api_gateway', 'API网关平台', 'jv-api-gateway-discovery', '/api/discovery/**', 'http://127.0.0.1:28083', NULL, 0, 1, '平台自身：服务发现'
WHERE NOT EXISTS (SELECT 1 FROM tb_gw_route WHERE path_prefix = '/api/discovery/**');

INSERT INTO tb_gw_route (app_id, app_name, service_name, path_prefix, upstream_url, prefix_path, strip_prefix, status, remark)
SELECT 'app-api_gateway', 'API网关平台', 'jv-api-gateway-trace', '/api/trace/**', 'http://127.0.0.1:28084', NULL, 0, 1, '平台自身：日志上报'
WHERE NOT EXISTS (SELECT 1 FROM tb_gw_route WHERE path_prefix = '/api/trace/**');

INSERT INTO tb_gw_route (app_id, app_name, service_name, path_prefix, upstream_url, prefix_path, strip_prefix, status, remark)
SELECT 'app-api_gateway', 'API网关平台', 'jv-api-gateway-config', '/api/configs/**', 'http://127.0.0.1:28085', NULL, 0, 1, '平台自身：系统配置'
WHERE NOT EXISTS (SELECT 1 FROM tb_gw_route WHERE path_prefix = '/api/configs/**');

INSERT INTO tb_gw_route (app_id, app_name, service_name, path_prefix, upstream_url, prefix_path, strip_prefix, status, remark)
SELECT 'app-api_gateway', 'API网关平台', 'jv-api-gateway-user', '/api/open/**', 'http://127.0.0.1:28081', NULL, 0, 1, '平台自身：开放认证（供第三方平台对接）'
WHERE NOT EXISTS (SELECT 1 FROM tb_gw_route WHERE path_prefix = '/api/open/**');
