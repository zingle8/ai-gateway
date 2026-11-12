-- =============================================================================
-- jv-api-gateway-user | 分组与权限 | tb_perm_group / tb_perm_group_member /
--                            tb_perm_group_perm / tb_perm_menu / tb_perm_item
--
-- 由 PermSchemaInitializer 启动时自动执行（幂等）。
-- =============================================================================

-- 分组：ORG 部门分组（树形，parent_id）/ PERM 权限分组（扁平，绑权限点）
CREATE TABLE IF NOT EXISTS tb_perm_group (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  name        VARCHAR(64)  NOT NULL,
  code        VARCHAR(64)  NOT NULL,
  type        VARCHAR(16)  NOT NULL DEFAULT 'PERM',   -- ORG / PERM
  parent_id   BIGINT       NOT NULL DEFAULT 0,
  data_scope  VARCHAR(32)  NOT NULL DEFAULT 'SELF',   -- ALL / DEPT_AND_CHILD / DEPT / SELF
  leader      VARCHAR(32)  NULL,
  remark      VARCHAR(255) NULL,
  builtin     TINYINT      NOT NULL DEFAULT 0,
  status      TINYINT      NOT NULL DEFAULT 1,
  deleted     TINYINT      NOT NULL DEFAULT 0,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户分组：部门组织 + 权限分组';

-- 分组成员：用户在分组下即拥有该分组的权限点（多组取并集）
CREATE TABLE IF NOT EXISTS tb_perm_group_member (
  id       BIGINT AUTO_INCREMENT PRIMARY KEY,
  group_id BIGINT NOT NULL,
  user_id  BIGINT NOT NULL,
  UNIQUE KEY uk_group_user (group_id, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='分组成员';

-- 分组 ↔ 权限点
CREATE TABLE IF NOT EXISTS tb_perm_group_perm (
  id        BIGINT AUTO_INCREMENT PRIMARY KEY,
  group_id  BIGINT       NOT NULL,
  perm_code VARCHAR(128) NOT NULL,
  UNIQUE KEY uk_group_perm (group_id, perm_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='分组权限点';

-- 权限点字典的平台/菜单骨架（parent_id='' 为平台层）
CREATE TABLE IF NOT EXISTS tb_perm_menu (
  id        VARCHAR(64) PRIMARY KEY,                 -- p-console / m-auth-user
  name      VARCHAR(64) NOT NULL,
  parent_id VARCHAR(64) NOT NULL DEFAULT '',
  sort      INT         NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='平台与菜单';

-- 权限点：code 为前后端共用鉴权标识
CREATE TABLE IF NOT EXISTS tb_perm_item (
  id        BIGINT AUTO_INCREMENT PRIMARY KEY,
  menu_id   VARCHAR(64)  NOT NULL,
  func_name VARCHAR(64)  NOT NULL DEFAULT '通用',
  code      VARCHAR(128) NOT NULL,
  name      VARCHAR(128) NOT NULL,
  type      VARCHAR(16)  NOT NULL DEFAULT 'BUTTON',  -- BUTTON / DISPLAY / API
  api       VARCHAR(255) NULL,
  enabled   TINYINT      NOT NULL DEFAULT 1,
  UNIQUE KEY uk_code (code),
  KEY idx_menu (menu_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='权限点';

-- ============================== 种子：平台 / 菜单 ==============================
INSERT IGNORE INTO tb_perm_menu (id, name, parent_id, sort) VALUES
  ('p-console',          '网关控制台',   '',              0),
  ('m-overview',         '网关总览',     'p-console',     1),
  ('m-stats',            '数据统计',     'p-console',     2),
  ('m-discovery',        '服务发现',     'p-console',     3),
  ('m-trace',            '日志追踪',     'p-console',     4),
  ('m-onboard',          '接入中心',     'p-console',     5),
  ('m-onboard-system',   '系统接入',     'm-onboard',     1),
  ('m-onboard-auto',     '自动接入',     'm-onboard',     2),
  ('m-onboard-route',    '路由配置',     'm-onboard',     3),
  ('m-onboard-release',  '上线发布',     'm-onboard',     4),
  ('m-onboard-open',     '开放认证',     'm-onboard',     5),
  ('m-auth',             '认证与安全',   'p-console',     6),
  ('m-auth-user',        '用户管理',     'm-auth',        1),
  ('m-auth-login',       '登录方式',     'm-auth',        2),
  ('m-auth-sso',         '单点登录',     'm-auth',        3),
  ('m-auth-session',     '令牌与会话',   'm-auth',        4),
  ('m-auth-policy',      '安全策略',     'm-auth',        5),
  ('m-perm',             '分组与权限',   'p-console',     7),
  ('m-perm-group',       '分组管理',     'm-perm',        1),
  ('m-perm-org',         '部门组织',     'm-perm',        2),
  ('m-perm-menu',        '平台功能与菜单','m-perm',       3),
  ('m-system',           '系统管理',     'p-console',     8),
  ('m-system-role',      '角色管理',     'm-system',      1),
  ('m-system-setting',   '系统设置',     'm-system',      2),
  ('p-open',             '开放平台',     '',              1),
  ('m-open-key',         'API Key 管理', 'p-open',        1),
  ('m-open-usage',       '调用量报表',   'p-open',        2),
  ('p-account',          '个人中心',     '',              2),
  ('m-account-profile',  '账号信息',     'p-account',     1),
  ('m-account-bind',     '绑定管理',     'p-account',     2),
  ('m-account-security', '安全设置',     'p-account',     3);

-- ============================== 种子：权限点 ==============================
INSERT IGNORE INTO tb_perm_item (menu_id, func_name, code, name, type, api, enabled) VALUES
  ('m-overview',       '看板',     'overview.view',             '查看总览看板',       'DISPLAY', 'GET /api/overview/summary',         1),
  ('m-overview',       '看板',     'overview.alert.ack',        '处理告警',           'BUTTON',  'POST /api/overview/alerts/ack',     1),
  ('m-stats',          '报表',     'stats.view',                '查看统计报表',       'DISPLAY', 'GET /api/stats',                    1),
  ('m-stats',          '报表',     'stats.export',              '导出报表',           'BUTTON',  'GET /api/stats/export',             1),
  ('m-discovery',      '实例列表', 'discovery.view',            '查看服务与实例',     'DISPLAY', 'GET /api/discovery/services',       1),
  ('m-discovery',      '实例列表', 'discovery.instance.offline','手动下线实例',       'BUTTON',  'PUT /api/discovery/instances/offline',1),
  ('m-trace',          '检索',     'trace.view',                '检索调用链',         'DISPLAY', 'GET /api/trace/logs',               1),
  ('m-trace',          '检索',     'trace.detail',              '查看链路详情',       'DISPLAY', 'GET /api/trace/detail',             1),
  ('m-onboard-system', '接入列表', 'onboard.system.view',       '查看接入应用',       'DISPLAY', 'GET /api/onboard/apps',             1),
  ('m-onboard-system', '接入列表', 'onboard.system.edit',       '编辑接入应用',       'BUTTON',  'PUT /api/onboard/apps',             1),
  ('m-onboard-route',  '路由表',   'onboard.route.view',        '查看路由',           'DISPLAY', 'GET /api/onboard/routes',           1),
  ('m-onboard-route',  '路由表',   'onboard.route.edit',        '新增/编辑路由',      'BUTTON',  'POST|PUT /api/onboard/routes',      1),
  ('m-onboard-ratelimit','限流策略','onboard.ratelimit.view',  '查看限流策略',       'DISPLAY', 'GET /api/rate-limits',              1),
  ('m-onboard-ratelimit','限流策略','onboard.ratelimit.edit',  '编辑限流策略',       'BUTTON',  'POST|PUT|DELETE /api/rate-limits',  1),
  ('m-onboard-release','发布单',   'onboard.release.view',      '查看发布单',         'DISPLAY', 'GET /api/onboard/releases',         1),
  ('m-onboard-release','发布单',   'onboard.release.publish',   '执行发布',           'BUTTON',  'POST /api/onboard/releases/publish',1),
  ('m-onboard-open',   '开放认证', 'open.auth.verify',          '验证用户令牌',       'API',     'POST /api/open/auth/token/verify',  1),
  ('m-auth-user',      '用户列表', 'auth.user.view',            '查看用户列表',       'DISPLAY', 'GET /api/users',                    1),
  ('m-auth-user',      '用户列表', 'auth.user.create',          '新增用户',           'BUTTON',  'POST /api/users',                   1),
  ('m-auth-user',      '用户列表', 'auth.user.edit',            '编辑用户',           'BUTTON',  'PUT /api/users/{id}',               1),
  ('m-auth-user',      '用户列表', 'auth.user.delete',          '删除用户',           'BUTTON',  'DELETE /api/users/{id}',            1),
  ('m-auth-user',      '用户列表', 'auth.user.resetPwd',        '重置密码',           'BUTTON',  'PUT /api/users/{id}/password',      1),
  ('m-auth-login',     '登录因子', 'auth.login.view',           '查看登录因子',       'DISPLAY', 'GET /api/auth/login-methods',       1),
  ('m-auth-login',     '登录因子', 'auth.login.edit',           '开关登录因子',       'BUTTON',  'PUT /api/auth/login-methods',       1),
  ('m-auth-sso',       '身份提供方','auth.sso.view',            '查看 IdP',           'DISPLAY', 'GET /api/auth/sso/providers',       1),
  ('m-auth-sso',       '身份提供方','auth.sso.edit',            '配置 IdP',           'BUTTON',  'POST|PUT /api/auth/sso/providers',  1),
  ('m-auth-session',   '在线会话', 'auth.session.view',         '查看在线会话',       'DISPLAY', 'GET /api/auth/sessions',            1),
  ('m-auth-session',   '在线会话', 'auth.session.kick',         '强制下线',           'BUTTON',  'DELETE /api/auth/sessions/{id}',    1),
  ('m-auth-policy',    '登录保护', 'auth.policy.view',          '查看安全策略',       'DISPLAY', 'GET /api/auth/policy',              1),
  ('m-auth-policy',    '登录保护', 'auth.policy.edit',          '修改安全策略',       'BUTTON',  'PUT /api/auth/policy',              1),
  ('m-perm-group',     '分组列表', 'perm.group.view',           '查看分组',           'DISPLAY', 'GET /api/perm/groups',              1),
  ('m-perm-group',     '分组列表', 'perm.group.edit',           '新增/编辑分组',      'BUTTON',  'POST|PUT /api/perm/groups',         1),
  ('m-perm-group',     '成员',     'perm.group.member',         '管理分组成员',       'BUTTON',  'PUT /api/perm/groups/{id}/members', 1),
  ('m-perm-group',     '权限',     'perm.group.grant',          '分配分组权限',       'BUTTON',  'PUT /api/perm/groups/{id}/perms',   1),
  ('m-perm-org',       '组织树',   'perm.org.view',             '查看组织架构',       'DISPLAY', 'GET /api/perm/groups?type=ORG',     1),
  ('m-perm-org',       '组织树',   'perm.org.edit',             '维护部门',           'BUTTON',  'POST|PUT|DELETE /api/perm/groups',  1),
  ('m-perm-menu',      '权限点',   'perm.menu.view',            '查看权限点',         'DISPLAY', 'GET /api/perm/dictionary',          1),
  ('m-perm-menu',      '权限点',   'perm.menu.edit',            '维护权限点',         'BUTTON',  'POST|PUT|DELETE /api/perm/items',   1),
  ('m-system-role',    '角色列表', 'system.role.view',          '查看角色',           'DISPLAY', 'GET /api/roles',                    1),
  ('m-system-setting', '基础设置', 'system.setting.view',       '查看系统设置',       'DISPLAY', 'GET /api/configs',                  1),
  ('m-system-setting', '基础设置', 'system.setting.edit',       '修改系统设置',       'BUTTON',  'PUT /api/configs/{key}',            1),
  ('m-open-key',       'Key 列表', 'open.key.view',             '查看 API Key',       'DISPLAY', 'GET /api/keys',                     1),
  ('m-open-key',       'Key 列表', 'open.key.issue',            '签发 API Key',       'BUTTON',  'POST /api/keys',                    1),
  ('m-open-key',       'Key 列表', 'open.key.revoke',           '吊销 API Key',       'BUTTON',  'DELETE /api/keys/{id}',             1),
  ('m-open-key',       'Key 列表', 'open.key.rotate',           '密钥轮换（服务间）', 'API',     'POST /api/keys/{id}/rotate',        1),
  ('m-open-usage',     '调用量',   'open.usage.view',           '查看调用量',         'DISPLAY', 'GET /api/keys/usage',               1);

-- ============================== 种子：内置分组 / 部门 ==============================
-- id 显式指定，保证 parent_id 引用稳定；INSERT IGNORE 幂等
INSERT IGNORE INTO tb_perm_group (id, name, code, type, parent_id, data_scope, leader, remark, builtin, status) VALUES
  (1,   '齐墨科技',   'ORG_ROOT',       'ORG', 0,   'ALL',           '王峥',     '根组织',           0, 1),
  (2,   '研发中心',   'ORG_RD',         'ORG', 1,   'DEPT_AND_CHILD','平台管理员','',                0, 1),
  (3,   '平台组',     'ORG_RD_PLATFORM','ORG', 2,   'DEPT',          '李四',     '控制台与开放平台', 0, 1),
  (4,   '网关组',     'ORG_RD_GATEWAY', 'ORG', 2,   'DEPT',          '张三',     '网关内核与路由',   0, 1),
  (5,   '产品设计部', 'ORG_PD',         'ORG', 1,   'DEPT',          '王五',     '',                 0, 1),
  (101, '超级管理员', 'ROLE_SUPER',     'PERM',0,   'ALL',           NULL,       '内置：全部权限，不可删除', 1, 1),
  (102, '网关运维',   'ROLE_GW_OPS',    'PERM',0,   'ALL',           NULL,       '巡检、路由与发布', 0, 1),
  (103, '只读观察者', 'ROLE_VIEWER',    'PERM',0,   'SELF',          NULL,       '仅可浏览，无写操作', 0, 1);

-- 网关运维：绑定运维相关权限点（tb_perm_item 已种好，按 code 关联）
INSERT IGNORE INTO tb_perm_group_perm (group_id, perm_code)
SELECT 102, code FROM tb_perm_item
WHERE code IN ('overview.view','stats.view','discovery.view','trace.view','trace.detail',
               'onboard.route.view','onboard.route.edit','onboard.release.view','onboard.release.publish',
               'onboard.ratelimit.view','onboard.ratelimit.edit');

-- 只读观察者：仅浏览
INSERT IGNORE INTO tb_perm_group_perm (group_id, perm_code)
SELECT 103, code FROM tb_perm_item
WHERE code IN ('overview.view','stats.view','discovery.view','trace.view');

-- 内置 admin 加入超级管理员分组
INSERT IGNORE INTO tb_perm_group_member (group_id, user_id)
SELECT 101, id FROM tb_sys_user WHERE username = 'admin' AND deleted = 0 LIMIT 1;

-- 演示账号分组：dev/ops → 网关运维，viewer → 只读观察者
INSERT IGNORE INTO tb_perm_group_member (group_id, user_id)
SELECT 102, id FROM tb_sys_user WHERE username IN ('dev', 'ops') AND deleted = 0;

INSERT IGNORE INTO tb_perm_group_member (group_id, user_id)
SELECT 103, id FROM tb_sys_user WHERE username = 'viewer' AND deleted = 0;
