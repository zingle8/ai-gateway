-- =============================================================
--  jv-api-gateway-user | 鉴权风控（v0.2 第 13 项）
--
--  tb_login_log      登录历史（成功 / 失败 + 风险标记），是「异常登录识别」的
--                    数据源，同时可供令牌与会话页展示「最近登录」
--  tb_gw_risk_event  风控事件（用户 / API Key 统一登记、处置与留痕）
--
--  说明：处置留痕直接落在风控事件行的 action / handler / handle_remark /
--  handle_time 上；API Key 相关处置另外写入 tb_gw_api_key_log（既有审计表）。
--  本脚本由 jv-api-gateway-user 启动时幂等执行（PermSchemaInitializer）。
-- =============================================================

CREATE TABLE IF NOT EXISTS tb_login_log (
  id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  username    VARCHAR(64)  NOT NULL                COMMENT '登录账号',
  user_id     BIGINT       DEFAULT NULL            COMMENT '用户 ID（账号不存在时为空）',
  ip          VARCHAR(64)  DEFAULT NULL            COMMENT '来源 IP',
  user_agent  VARCHAR(255) DEFAULT NULL            COMMENT 'User-Agent',
  app_id      VARCHAR(64)  DEFAULT NULL            COMMENT '归属平台（console / 接入方 appId）',
  success     TINYINT      NOT NULL DEFAULT 0      COMMENT '1 成功 0 失败',
  fail_reason VARCHAR(128) DEFAULT NULL            COMMENT '失败原因',
  fail_count  INT          DEFAULT NULL            COMMENT '当次失败后的连续失败次数',
  risk_type   VARCHAR(64)  DEFAULT NULL            COMMENT '风险类型（逗号分隔多个）',
  risk_level  VARCHAR(16)  DEFAULT NULL            COMMENT '风险等级 HIGH / MEDIUM / LOW',
  risk_reason VARCHAR(255) DEFAULT NULL            COMMENT '风险说明',
  login_time  DATETIME     NOT NULL                COMMENT '登录时间',
  PRIMARY KEY (id),
  KEY idx_user_time (username, login_time),
  KEY idx_time (login_time),
  KEY idx_risk (risk_level, login_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '登录历史与异常登录风险标记';

CREATE TABLE IF NOT EXISTS tb_gw_risk_event (
  id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  subject_type  VARCHAR(16)  NOT NULL                COMMENT '主体类型 USER / API_KEY',
  subject_id    VARCHAR(64)  NOT NULL                COMMENT '主体标识：用户 ID 或 Key 短标识',
  subject_name  VARCHAR(128) DEFAULT NULL            COMMENT '主体名称：账号 / Key 名称',
  risk_type     VARCHAR(32)  NOT NULL                COMMENT 'LOGIN_NEW_IP / LOGIN_OFF_HOURS / LOGIN_BRUTE_FORCE / KEY_SPIKE / KEY_ERROR_RATE / KEY_EXPIRING / KEY_UNUSED',
  risk_level    VARCHAR(16)  NOT NULL DEFAULT 'MEDIUM' COMMENT 'HIGH / MEDIUM / LOW',
  detail        VARCHAR(500) DEFAULT NULL            COMMENT '风险详情',
  status        VARCHAR(16)  NOT NULL DEFAULT 'OPEN'  COMMENT 'OPEN 待处置 / HANDLED 已处置 / IGNORED 已忽略',
  action        VARCHAR(32)  DEFAULT NULL            COMMENT '处置动作：ACK / DISABLE_USER / ENABLE_USER / UNLOCK_USER / KICK_USER / DISABLE_KEY / ENABLE_KEY',
  handler       VARCHAR(64)  DEFAULT NULL            COMMENT '处置人',
  handle_remark VARCHAR(255) DEFAULT NULL            COMMENT '处置备注',
  handle_time   DATETIME     DEFAULT NULL            COMMENT '处置时间',
  create_time   DATETIME     NOT NULL                COMMENT '事件产生时间',
  PRIMARY KEY (id),
  KEY idx_subject (subject_type, subject_id, create_time),
  KEY idx_status (status, create_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '鉴权风控事件（用户 / API Key）';

-- ============================== 菜单与权限点 ==============================
INSERT IGNORE INTO tb_perm_menu (id, name, parent_id, sort) VALUES
  ('m-auth-risk', '风控中心', 'm-auth', 7);

INSERT IGNORE INTO tb_perm_item (menu_id, func_name, code, name, type, api, enabled) VALUES
  ('m-auth-risk', '风险事件', 'auth.risk.view',   '查看风控中心',   'DISPLAY', 'GET /api/risk/events',              1),
  ('m-auth-risk', '风险事件', 'auth.risk.handle', '处置风险事件',   'BUTTON',  'POST /api/risk/events/{id}/handle', 1);

-- 网关运维组默认可见并处置（超级管理员为内置分组，自动拥有全部权限点）
INSERT IGNORE INTO tb_perm_group_perm (group_id, perm_code)
SELECT 102, code FROM tb_perm_item WHERE code IN ('auth.risk.view', 'auth.risk.handle');

-- ============================== IP 封禁（v0.2 多端与安全增强） ==============================
-- 手动 / 风控处置封禁来源 IP：登录拒绝（user）+ 网关全量拦截（proxy 读 Redis set gw:ipban）
CREATE TABLE IF NOT EXISTS tb_gw_ip_ban (
  id         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  ip         VARCHAR(64)  NOT NULL                COMMENT '被封禁的 IP（支持前缀通配 10.0.0.*）',
  reason     VARCHAR(255) DEFAULT NULL            COMMENT '封禁原因',
  operator   VARCHAR(64)  DEFAULT NULL            COMMENT '操作人（system=自动）',
  status     TINYINT      NOT NULL DEFAULT 1      COMMENT '1 生效 / 0 已解封',
  banned_at  DATETIME     NOT NULL                COMMENT '封禁时间',
  unbanned_at DATETIME    DEFAULT NULL            COMMENT '解封时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_ip (ip)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'IP 封禁清单';
