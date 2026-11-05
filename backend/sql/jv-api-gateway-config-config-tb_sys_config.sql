-- =============================================================================
-- jv-api-gateway-config | 系统配置 | tb_sys_config
--
-- 平台级配置的统一存储：系统设置页读写，其他模块（proxy 鉴权缓存模式、
-- 菜单健康标识、开放认证开关等）定时轮询本表实现"修改实时生效"。
-- 由 ConfigSchemaInitializer 启动时自动执行（幂等）。
-- =============================================================================
USE api_gateway;

CREATE TABLE IF NOT EXISTS tb_sys_config (
  id           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  config_key   VARCHAR(128) NOT NULL                COMMENT '配置键，如 platform.auth.cache.mode',
  config_value VARCHAR(255) NOT NULL                COMMENT '配置值',
  value_type   VARCHAR(16)  NOT NULL DEFAULT 'STRING' COMMENT '值类型：BOOLEAN/STRING/NUMBER',
  remark       VARCHAR(255)          DEFAULT NULL   COMMENT '说明',
  update_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_key (config_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='平台系统配置';

-- ============================== 种子：平台配置 ==============================
INSERT IGNORE INTO tb_sys_config (config_key, config_value, value_type, remark) VALUES
  ('platform.menu.health.enabled', 'true', 'BOOLEAN', '侧边栏菜单后缀的服务运行状态标识（绿/灰/红小圆点）开关'),
  ('platform.auth.cache.mode',     'auto', 'STRING',  '网关鉴权缓存：auto=Redis 可用则用 Redis 否则本地缓存 / redis=强制 Redis / local=仅本地缓存。MySQL 始终为源数据'),
  ('platform.open.auth.enabled',   'true', 'BOOLEAN', '开放认证接口开关（/api/open/auth/*，供第三方平台注册/登录/验签）');
