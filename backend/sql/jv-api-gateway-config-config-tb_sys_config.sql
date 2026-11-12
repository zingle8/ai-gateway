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

-- 安全策略 / 登录方式为 JSON 长文本，扩容 config_value
ALTER TABLE tb_sys_config MODIFY COLUMN config_value TEXT NOT NULL;

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

INSERT INTO tb_sys_config
  (config_key, config_value, value_type, config_source, source_ref, platform_name, app_name, service_name, remark)
SELECT 'platform.open.auth.require-key', 'false', 'BOOLEAN', 'DB', NULL,
       'API网关平台', 'jv-api-gateway', '*',
       '开放接口调用方约束开关（true 时 /api/open/** 必须携带 X-Api-Key 并通过 IP / 服务 / 平台白名单校验）'
WHERE NOT EXISTS (SELECT 1 FROM tb_sys_config WHERE config_key = 'platform.open.auth.require-key');

INSERT INTO tb_sys_config
  (config_key, config_value, value_type, config_source, source_ref, platform_name, app_name, service_name, remark)
SELECT 'platform.log.levels', '{}', 'JSON', 'DB', NULL,
       'API网关平台', 'jv-api-gateway', '*',
       '动态日志级别（logger → 级别，各服务 10 秒内应用，免重启）'
WHERE NOT EXISTS (SELECT 1 FROM tb_sys_config WHERE config_key = 'platform.log.levels');

INSERT INTO tb_sys_config
  (config_key, config_value, value_type, config_source, source_ref, platform_name, app_name, service_name, remark)
SELECT 'platform.session.mode', 'MULTI', 'STRING', 'DB', NULL,
       'API网关平台', 'jv-api-gateway', '*',
       '多端登录策略：SINGLE=单端互踢（新登录踢掉旧会话）/ MULTI=多端共存（默认），登录时校验，10 秒内生效'
WHERE NOT EXISTS (SELECT 1 FROM tb_sys_config WHERE config_key = 'platform.session.mode');

INSERT INTO tb_sys_config
  (config_key, config_value, value_type, config_source, source_ref, platform_name, app_name, service_name, remark)
SELECT 'platform.session.max', '5', 'INTEGER', 'DB', NULL,
       'API网关平台', 'jv-api-gateway', '*',
       'MULTI 模式下的并发会话上限（1~50）：超限自动踢最早会话；SINGLE 模式忽略本项'
WHERE NOT EXISTS (SELECT 1 FROM tb_sys_config WHERE config_key = 'platform.session.max');

INSERT INTO tb_sys_config
  (config_key, config_value, value_type, config_source, source_ref, platform_name, app_name, service_name, remark)
SELECT 'platform.security.policy', '{"lockThreshold":5,"lockMinutes":30,"captchaTrigger":"FAIL2","maxSessions":3,"otpTtl":5,"otpInterval":60,"otpDailyLimit":10,"otpMaxAttempts":5,"pwdMinLength":8,"pwdUpper":true,"pwdDigit":true,"pwdSymbol":false,"pwdExpireDays":90,"pwdHistory":true,"pwdHistoryCount":5,"mfaScope":"NONE","stepUp":true,"totpDrift":1,"backupCodes":true,"accessConsole":20,"accessApi":60,"refreshDays":7,"refreshIdleDays":30,"refreshRotate":true}', 'JSON', 'DB', NULL,
       'API网关平台', 'jv-api-gateway', '*',
       '平台安全策略（登录保护/验证码/密码/MFA/令牌有效期），控制台「安全策略」页维护，登录流程实时读取'
WHERE NOT EXISTS (SELECT 1 FROM tb_sys_config WHERE config_key = 'platform.security.policy');

INSERT INTO tb_sys_config
  (config_key, config_value, value_type, config_source, source_ref, platform_name, app_name, service_name, remark)
SELECT 'platform.login.methods', '{"factors":[{"key":"username","name":"用户名 + 密码","tier":"主因子","enabled":true},{"key":"email","name":"邮箱 + 密码","tier":"主因子","enabled":true},{"key":"phone","name":"手机号 + 密码","tier":"主因子","enabled":true},{"key":"emailOtp","name":"邮箱验证码免密登录","tier":"主因子","enabled":true},{"key":"smsOtp","name":"短信验证码免密登录","tier":"主因子","enabled":false},{"key":"qrcode","name":"扫码登录","tier":"主因子","enabled":false},{"key":"sso","name":"单点登录 SSO","tier":"主因子","enabled":true},{"key":"totp","name":"动态码二次验证（TOTP）","tier":"二次验证","enabled":true}],"mail":{"enabled":true,"host":"","port":465,"ssl":"SSL","from":"","secret":""},"sms":{"enabled":false,"provider":"ALIYUN","sign":"","template":"","keyId":""}}', 'JSON', 'DB', NULL,
       'API网关平台', 'jv-api-gateway', '*',
       '登录方式配置（因子开关 + 邮件/短信通道），控制台「登录方式」页维护；username 因子关闭时密码登录被拒'
WHERE NOT EXISTS (SELECT 1 FROM tb_sys_config WHERE config_key = 'platform.login.methods');



INSERT INTO tb_sys_config
  (config_key, config_value, value_type, config_source, source_ref, platform_name, app_name, service_name, remark)
SELECT 'platform.ui.refresh.interval', '10', 'INTEGER', 'DB', NULL,
       'API网关平台', 'jv-api-gateway', '*',
       '控制台数据自动刷新间隔（秒）：网关总览 / 数据统计 / 服务发现的轮询频率（含服务健康状态：健康/异常/离线），2~300，30 秒内生效'
WHERE NOT EXISTS (SELECT 1 FROM tb_sys_config WHERE config_key = 'platform.ui.refresh.interval');

INSERT INTO tb_sys_config
  (config_key, config_value, value_type, config_source, source_ref, platform_name, app_name, service_name, remark)
SELECT 'platform.auth.exemption.control', 'CLIENT', 'STRING', 'DB', NULL,
       'API网关平台', 'jv-api-gateway', '*',
       '鉴权豁免权全局控制：CLIENT=由 client/sdk 端自行配置豁免（zimo.gateway.client.auth.exempt-paths，现状）；PLATFORM=由平台端统一控制（应用/服务/接口级精细豁免管理，规划中的菜单功能）。精细豁免功能上线前该配置仅作为控制位记录'
WHERE NOT EXISTS (SELECT 1 FROM tb_sys_config WHERE config_key = 'platform.auth.exemption.control');
