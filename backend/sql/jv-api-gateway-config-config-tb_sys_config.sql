-- =============================================================================
-- jv-api-gateway-config | 平台配置 | tb_sys_config
--
-- 元数据（key / 来源 / 归属 / 类型 / 备注）始终存本表；
-- 配置值按 config_source 存在不同存储：
--   DB    —— 本表 config_value（默认，零依赖）
--   REDIS —— source_ref 指定的 Hash（field = config_key），表内存最后写入快照
--   NACOS —— source_ref 指定的 dataId|group（properties 文本），表内存最后写入快照
-- 远端不可达时，接口返回快照值并标记 STALE，控制台会提示。
-- =============================================================================
USE api_gateway;

CREATE TABLE IF NOT EXISTS tb_sys_config (
  id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  config_key    VARCHAR(128) NOT NULL                COMMENT '配置键，如 platform.auth.cache.mode',
  config_value  VARCHAR(512) NOT NULL                COMMENT '配置值（非 DB 来源时为最后写入快照）',
  value_type    VARCHAR(16)  NOT NULL DEFAULT 'STRING' COMMENT '值类型：BOOLEAN/STRING/NUMBER',
  config_source VARCHAR(16)  NOT NULL DEFAULT 'DB'     COMMENT '存储来源：DB/REDIS/NACOS',
  source_ref    VARCHAR(255)          DEFAULT NULL     COMMENT '来源定位：Redis=Hash键，Nacos=dataId|groupName',
  platform_name VARCHAR(64)  NOT NULL DEFAULT 'API网关平台' COMMENT '所属平台',
  app_name      VARCHAR(64)  NOT NULL DEFAULT 'jv-api-gateway' COMMENT '所属应用',
  service_name  VARCHAR(64)  NOT NULL DEFAULT '*'      COMMENT '所属服务，* 表示平台级',
  remark        VARCHAR(255)          DEFAULT NULL     COMMENT '说明',
  update_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_key (config_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='平台系统配置（元数据 + 值快照）';

-- 存量库补列（MySQL 5.7 无 ADD COLUMN IF NOT EXISTS，应用启动时由
-- ConfigSchemaInitializer 用 DatabaseMetaData 判断后执行同样的 ALTER）
-- ALTER TABLE tb_sys_config ADD COLUMN config_source VARCHAR(16) NOT NULL DEFAULT 'DB' AFTER value_type;
-- ALTER TABLE tb_sys_config ADD COLUMN source_ref VARCHAR(255) DEFAULT NULL AFTER config_source;
-- ALTER TABLE tb_sys_config ADD COLUMN platform_name VARCHAR(64) NOT NULL DEFAULT 'API网关平台' AFTER source_ref;
-- ALTER TABLE tb_sys_config ADD COLUMN app_name VARCHAR(64) NOT NULL DEFAULT 'jv-api-gateway' AFTER platform_name;
-- ALTER TABLE tb_sys_config ADD COLUMN service_name VARCHAR(64) NOT NULL DEFAULT '*' AFTER app_name;
-- ALTER TABLE tb_sys_config MODIFY COLUMN config_value VARCHAR(512) NOT NULL;

INSERT INTO tb_sys_config
  (config_key, config_value, value_type, config_source, source_ref, platform_name, app_name, service_name, remark)
SELECT 'platform.menu.health.enabled', 'true', 'BOOLEAN', 'DB', NULL,
       'API网关平台', 'jv-api-gateway', '*',
       '侧边栏菜单后缀的服务运行状态标识（绿/灰/红小圆点）开关'
WHERE NOT EXISTS (SELECT 1 FROM tb_sys_config WHERE config_key = 'platform.menu.health.enabled');

INSERT INTO tb_sys_config
  (config_key, config_value, value_type, config_source, source_ref, platform_name, app_name, service_name, remark)
SELECT 'platform.auth.cache.mode', 'auto', 'STRING', 'DB', NULL,
       'API网关平台', 'jv-api-gateway', '*',
       '网关鉴权缓存：auto=Redis 可用则用 Redis 否则本地缓存 / redis=强制 Redis / local=仅本地缓存。MySQL 始终为源数据'
WHERE NOT EXISTS (SELECT 1 FROM tb_sys_config WHERE config_key = 'platform.auth.cache.mode');

INSERT INTO tb_sys_config
  (config_key, config_value, value_type, config_source, source_ref, platform_name, app_name, service_name, remark)
SELECT 'platform.open.auth.enabled', 'true', 'BOOLEAN', 'DB', NULL,
       'API网关平台', 'jv-api-gateway', '*',
       '开放认证接口开关（/api/open/auth/*，供第三方平台注册/登录/验签）'
WHERE NOT EXISTS (SELECT 1 FROM tb_sys_config WHERE config_key = 'platform.open.auth.enabled');
