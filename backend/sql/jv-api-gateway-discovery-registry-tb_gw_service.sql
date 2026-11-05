-- =============================================================================
-- jv-api-gateway-discovery | 服务注册 | tb_gw_service / tb_gw_api
--
-- 服务发现模块的注册表与接口清单（含演示数据）。
-- 服务实例由 SDK 心跳与平台自身模块自动写入，本脚本仅做初始演示数据。
-- =============================================================================
USE api_gateway;

-- ----------------------------
-- 2. 注册服务表（服务发现）
-- ----------------------------

CREATE TABLE IF NOT EXISTS tb_gw_service (
  id            BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
  app_id        VARCHAR(64) NOT NULL                COMMENT '应用编码，如 app-order',
  app_name      VARCHAR(64) NOT NULL                COMMENT '应用名称，如 订单中心',
  service_name  VARCHAR(64) NOT NULL                COMMENT '服务名，如 order-service',
  ip            VARCHAR(64)          DEFAULT NULL   COMMENT '实例 IP，SDK 心跳上报',
  port          INT         NOT NULL                COMMENT '服务端口',
  language      VARCHAR(32)          DEFAULT NULL   COMMENT '语言类型，如 Java 17',
  version       VARCHAR(32)          DEFAULT NULL   COMMENT '版本号，如 v2.4.0',
  qps           INT                  DEFAULT 0     COMMENT '实时 QPS',
  avg_rt        INT                  DEFAULT 0     COMMENT '平均响应时间（ms）',
  status        VARCHAR(16) NOT NULL DEFAULT '健康' COMMENT '服务状态：健康/亚健康/异常',
  in_flow       VARCHAR(32)          DEFAULT NULL   COMMENT '入站流量',
  out_flow      VARCHAR(32)          DEFAULT NULL   COMMENT '出网流量',
  instances     INT                  DEFAULT 1     COMMENT '实例数',
  register_time DATETIME             DEFAULT NULL   COMMENT '注册时间',
  last_heartbeat_time DATETIME             DEFAULT NULL   COMMENT '最近心跳时间（自动接入服务持续刷新）',
  create_time   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted       TINYINT     NOT NULL DEFAULT 0     COMMENT '逻辑删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_service (service_name),
  KEY idx_app (app_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '注册服务';

-- ----------------------------
-- 3. 网关接口表（接口个数统计）
-- ----------------------------

CREATE TABLE IF NOT EXISTS tb_gw_api (
  id           BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
  app_id       VARCHAR(64) NOT NULL                COMMENT '所属应用编码',
  service_name VARCHAR(64) NOT NULL                COMMENT '所属服务名',
  method       VARCHAR(16) NOT NULL                COMMENT 'HTTP 方法',
  path         VARCHAR(255) NOT NULL               COMMENT '接口路径',
  status       VARCHAR(16) NOT NULL DEFAULT '在线' COMMENT '接口状态：在线/已下线',
  create_time  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted      TINYINT     NOT NULL DEFAULT 0      COMMENT '逻辑删除',
  PRIMARY KEY (id),
  KEY idx_service (service_name)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '网关接口';

-- ----------------------------
-- 4. 网关调用日志表（日志追踪）
-- ----------------------------
DROP TABLE IF EXISTS tb_gw_trace_log;
CREATE TABLE tb_gw_trace_log (
  id           BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
  app_id       VARCHAR(64)           DEFAULT NULL   COMMENT '应用编码',
  app_name     VARCHAR(64)           DEFAULT NULL   COMMENT '应用名称',
  service_name VARCHAR(64)           DEFAULT NULL   COMMENT '命中的后端服务',
  api          VARCHAR(255)          DEFAULT NULL   COMMENT '接口，如 POST /api/v1/order/create',
  req_params   TEXT                                COMMENT '请求 Query 参数（原始 query string；表单参数在 req_body）',
  trace_id     VARCHAR(64)           DEFAULT NULL   COMMENT '链路追踪 ID',
  protocol     VARCHAR(16)           DEFAULT NULL   COMMENT '协议：HTTP/1.1 / HTTP/2 / gRPC / WebSocket',
  req_format   VARCHAR(32)           DEFAULT NULL   COMMENT '请求格式',
  res_format   VARCHAR(32)           DEFAULT NULL   COMMENT '返回格式',
  req_ip       VARCHAR(64)           DEFAULT NULL   COMMENT '调用方 IP',
  req_port     INT                   DEFAULT NULL   COMMENT '调用方端口',
  res_ip       VARCHAR(64)           DEFAULT NULL   COMMENT '后端实例 IP',
  res_port     INT                   DEFAULT NULL   COMMENT '后端实例端口',
  status_code  INT                   DEFAULT NULL   COMMENT '响应状态码',
  err_type     VARCHAR(32)           DEFAULT NULL   COMMENT '网关错误类型',
  cost         INT                   DEFAULT NULL   COMMENT '耗时（ms）',
  req_time     DATETIME              DEFAULT NULL   COMMENT '请求时间',
  res_time     DATETIME              DEFAULT NULL   COMMENT '返回时间',
  req_header   TEXT                                COMMENT '请求 Header',
  res_header   TEXT                                COMMENT '响应 Header',
  req_body     TEXT                                COMMENT '请求报文体',
  res_body     TEXT                                COMMENT '返回报文体',
  req_bytes    BIGINT               DEFAULT 0      COMMENT '请求字节数',
  res_bytes    BIGINT               DEFAULT 0      COMMENT '返回字节数',
  create_time  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  deleted      TINYINT     NOT NULL DEFAULT 0      COMMENT '逻辑删除',
  PRIMARY KEY (id),
  KEY idx_trace (trace_id),
  KEY idx_service (service_name),
  KEY idx_errtype (err_type),
  KEY idx_reqtime (req_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '网关调用日志';

SET FOREIGN_KEY_CHECKS = 1;

-- =============================================================
--  初始数据：注册服务（18 条，对应 demo 列表 + 扩展）
-- =============================================================
INSERT INTO tb_gw_service (app_id, app_name, service_name, port, language, version, qps, avg_rt, status, in_flow, out_flow, instances, register_time) VALUES
('app-order',     '订单中心',   'order-service',     8080, 'Java 17',      'v2.4.0', 4820, 168, '健康',   '3.8 GB',  '26.4 GB', 12, '2024-03-18 10:24:06'),
('app-user',      '用户中心',   'user-service',      8081, 'Java 17',      'v1.9.2', 3260, 92,  '健康',   '2.1 GB',  '18.7 GB', 8,  '2024-05-02 14:11:38'),
('app-search',    '搜索服务',   'search-service',    8082, 'Java 17',      'v3.1.0', 2140, 236, '亚健康', '1.6 GB',  '42.3 GB', 16, '2024-01-09 09:37:52'),
('app-file',      '文件服务',   'file-service',      8083, 'Go 1.22',      'v1.4.6', 860,  312, '健康',   '8.9 GB',  '9.2 GB',  6,  '2024-06-21 16:05:19'),
('app-pay',      '支付中心',   'pay-service',       8084, 'Java 17',      'v0.8.1', 1580, 204, '健康',   '0.9 GB',  '5.8 GB',  10, '2024-02-14 11:48:03'),
('app-auth',      '统一认证',   'auth-service',      8443, 'Go 1.22',      'v2.0.3', 6420, 38,  '健康',   '1.2 GB',  '3.6 GB',  8,  '2023-11-27 08:52:44'),
('app-msg',       '消息通知',  'msg-service',       8085, 'Node.js 20',   'v1.7.0', 1180, 128, '健康',   '0.4 GB',  '2.4 GB',  6,  '2024-07-08 13:26:57'),
('app-inventory', '库存中心',   'inventory-service', 8086, 'Go 1.22',      'v2.6.1', 1980, 76,  '健康',   '0.7 GB',  '4.1 GB',  8,  '2024-08-03 10:07:41'),
('app-coupon',    '优惠券中心', 'coupon-service',    8087, 'Java 17',      'v1.2.0', 1420, 142, '健康',   '0.5 GB',  '3.2 GB',  4,  '2024-09-15 17:22:08'),
('app-settle',    '结算中心',   'settle-service',    8088, 'Java 17',      'v3.0.2', 640,  268, '健康',   '0.3 GB',  '2.8 GB',  6,  '2024-05-30 12:14:35'),
('app-report',    '报表中心',   'report-service',     8089, 'Node.js 20',   'v0.9.4', 210,  1840,'异常',   '0.2 GB',  '6.4 GB',  4,  '2024-10-11 09:58:26'),
('app-open',      '开放平台',   'open-service',       9000, 'Java 17',      'v2.1.0', 2680, 118, '健康',   '2.4 GB',  '12.6 GB', 10, '2024-04-26 15:39:12'),
('app-order',     '订单中心',   'order-query-service',8090, 'Java 17',      'v1.5.3', 1240, 88,  '健康',   '0.6 GB',  '7.2 GB',  4,  '2024-08-19 11:32:47'),
('app-user',      '用户中心',   'user-profile-service',8091, 'Java 17', 'v1.1.0', 980,  64,  '健康',   '0.4 GB',  '3.9 GB',  4,  '2024-09-02 10:18:29'),
('app-pay',      '支付中心',   'pay-refund-service', 8092, 'Java 17',      'v0.6.2', 520,  186, '健康',   '0.2 GB',  '1.8 GB',  4,  '2024-10-05 14:41:03'),
('app-auth',      '统一认证',   'auth-sso-service',     8093, 'Go 1.22',      'v1.3.1', 1840, 52,  '健康',   '0.3 GB',  '1.4 GB',  4,  '2024-07-22 09:26:18'),
('app-msg',       '消息通知',  'msg-push-service',    8094, 'Node.js 20',   'v0.7.5', 760,  142, '健康',   '0.2 GB',  '1.6 GB',  4,  '2024-10-18 16:52:40');

-- =============================================================
--  初始数据：网关接口（42 条）
-- =============================================================
INSERT INTO tb_gw_api (app_id, service_name, method, path, status) VALUES
('app-order',     'order-service',       'POST',   '/api/v1/order/create',        '在线'),
('app-order',     'order-service',       'GET',    '/api/v1/order/detail',       '在线'),
('app-order',     'order-service',       'POST',   '/api/v1/order/cancel',       '在线'),
('app-order',     'order-query-service', 'GET',    '/api/v1/order/list',         '在线'),
('app-order',     'order-query-service', 'POST',   '/api/v1/order/export',       '在线'),
('app-user',      'user-service',        'GET',    '/api/v1/user/profile',      '在线'),
('app-user',      'user-service',        'POST',   '/api/v1/user/profile',      '在线'),
('app-user',      'user-service',        'POST',   '/api/v1/user/logout',       '在线'),
('app-user',      'user-profile-service','GET',    '/api/v1/user/tags',         '在线'),
('app-user',      'user-profile-service','PUT',    '/api/v1/user/settings',    '在线'),
('app-search',    'search-service',      'GET',    '/api/v1/search',            '在线'),
('app-search',    'search-service',      'POST',   '/api/v1/search/suggest',    '在线'),
('app-search',    'search-service',      'GET',    '/api/v1/search/hot',        '在线'),
('app-file',      'file-service',        'POST',   '/api/v1/file/upload',      '在线'),
('app-file',      'file-service',        'GET',    '/api/v1/file/download',    '在线'),
('app-file',      'file-service',        'DELETE', '/api/v1/file/delete',       '在线'),
('app-pay',       'pay-service',         'POST',   '/api/v1/pay/confirm',       '在线'),
('app-pay',       'pay-service',         'GET',    '/api/v1/pay/result',        '在线'),
('app-pay',       'pay-refund-service',  'POST',   '/api/v1/pay/refund',        '在线'),
('app-auth',      'auth-service',        'POST',   '/api/v1/oauth/token',       '在线'),
('app-auth',      'auth-service',        'POST',   '/api/v1/oauth/verify',      '在线'),
('app-auth',      'auth-sso-service',    'GET',    '/api/v1/sso/session',      '在线'),
('app-msg',       'msg-service',         'POST',   '/api/v1/msg/send',        '在线'),
('app-msg',       'msg-push-service',   'POST',   '/api/v1/msg/push',        '在线'),
('app-msg',       'msg-push-service',    'GET',    '/api/v1/msg/status',     '在线'),
('app-inventory', 'inventory-service',   'GET',    '/api/v1/inventory/query','在线'),
('app-inventory', 'inventory-service',   'POST',   '/api/v1/inventory/lock','在线'),
('app-inventory', 'inventory-service',   'POST',   '/api/v1/inventory/unlock','已下线'),
('app-coupon',    'coupon-service',      'POST',   '/api/v1/coupon/use',     '在线'),
('app-coupon',    'coupon-service',      'GET',    '/api/v1/coupon/list',    '在线'),
('app-settle',    'settle-service',      'GET',    '/api/v1/settle/bill',  '在线'),
('app-settle',    'settle-service',      'POST',   '/api/v1/settle/audit','在线'),
('app-report',    'report-service',      'POST',   '/api/v1/report/export','在线'),
('app-report',    'report-service',      'GET',    '/api/v1/report/list',   '在线'),
('app-open',      'open-service',        'POST',   '/api/v1/open/subscribe','在线'),
('app-open',      'open-service',        'GET',    '/api/v1/open/apps',    '在线'),
('app-open',      'open-service',        'POST',   '/api/v1/open/callback','在线'),
('app-order',     'order-service',       'GET',    '/api/v1/order/status',  '在线'),
('app-user',      'user-service',       'POST',   '/api/v1/user/register', '在线'),
('app-pay',       'pay-service',         'POST',   '/api/v1/pay/prepay',    '在线'),
('app-inventory', 'inventory-service',   'GET',    '/api/v1/inventory/logs','在线');
