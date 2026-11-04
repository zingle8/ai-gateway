-- =============================================================
--  AI 网关平台 - 数据库初始化脚本
--  数据库：api_gateway
--  说明：账号不在此脚本写入，首次启动 jv-gateway-user 服务时自动初始化
-- =============================================================

CREATE DATABASE IF NOT EXISTS api_gateway
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_general_ci;

USE api_gateway;

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- 1. 平台用户表
-- ----------------------------
DROP TABLE IF EXISTS tb_sys_user;
CREATE TABLE tb_sys_user (
  id               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  username         VARCHAR(64)  NOT NULL                COMMENT '登录账号',
  password         VARCHAR(128) NOT NULL                COMMENT '登录密码（BCrypt）',
  nickname         VARCHAR(64)           DEFAULT NULL   COMMENT '用户姓名',
  email            VARCHAR(128)          DEFAULT NULL   COMMENT '邮箱',
  phone            VARCHAR(32)           DEFAULT NULL   COMMENT '手机号',
  role             VARCHAR(32)  NOT NULL DEFAULT 'VIEWER' COMMENT '角色：ADMIN/OPERATOR/VIEWER',
  status           TINYINT      NOT NULL DEFAULT 1     COMMENT '状态：1 启用，0 禁用',
  last_login_time  DATETIME              DEFAULT NULL   COMMENT '最后登录时间',
  last_login_ip    VARCHAR(64)           DEFAULT NULL   COMMENT '最后登录 IP',
  token_version    INT          NOT NULL DEFAULT 0     COMMENT '令牌版本：退出登录时自增，用于让已签发的 JWT 立即失效',
  create_time      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted          TINYINT      NOT NULL DEFAULT 0     COMMENT '逻辑删除：0 未删除，1 已删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_username (username),
  KEY idx_role (role),
  KEY idx_status (status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '平台用户';

-- ----------------------------
-- 2. 注册服务表（服务发现）
-- ----------------------------
DROP TABLE IF EXISTS tb_gw_service;
CREATE TABLE tb_gw_service (
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
DROP TABLE IF EXISTS tb_gw_api;
CREATE TABLE tb_gw_api (
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

-- =============================================================
--  初始数据：调用日志（12 条，对应 demo 列表）
--  err_type：无 / 鉴权失败 / 路由失败 / 限流拒绝 / 参数校验失败 / 上游响应超时 / 上游响应异常
-- =============================================================
INSERT INTO tb_gw_trace_log
(app_id, app_name, service_name, api, trace_id, protocol, req_format, res_format,
 req_ip, req_port, res_ip, res_port, status_code, err_type, cost, req_time, res_time,
 req_header, res_header, req_body, res_body, req_bytes, res_bytes)
VALUES
('app-order','订单中心','order-service','POST /api/v1/order/create','6f2c1a9b8e7d4c3f','HTTP/1.1','JSON','JSON','10.20.3.11',52134,'10.20.16.7',8080,200,'无',186,'2026-10-30 15:42:10','2026-10-30 15:42:10',
'POST /api/v1/order/create HTTP/1.1\nHost: api-gw.example.com\nContent-Type: application/json;charset=UTF-8\nX-AppCode: app-order\nX-Request-Id: 6f2c1a9b8e7d4c3f\nX-Forwarded-For: 10.20.3.11\nAuthorization: Bearer eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.6f2c1a9b\nUser-Agent: okhttp/4.12.0\nContent-Length: 214\nAccept-Encoding: gzip, deflate',
'HTTP/1.1 200 OK\nContent-Type: application/json;charset=UTF-8\nContent-Length: 168\nX-Response-Time: 186ms\nX-Gateway-Node: gw-node-07\nX-Upstream-Host: 10.20.16.7:8080\nX-Trace-Id: 6f2c1a9b8e7d4c3f\nDate: Fri, 30 Oct 2026 07:42:11 GMT',
'{\n  "appCode": "app-order",\n  "orderNo": "SO20261030001542",\n  "amount": 299.00,\n  "items": [\n    { "skuId": "SKU-88231", "qty": 2 }\n  ]\n}',
'{\n  "code": 0,\n  "data": {\n    "orderId": "ORD20261030001542",\n    "status": "CREATED"\n  },\n  "message": "ok"\n}',
214, 168),

('app-search','搜索服务','search-service','GET /api/v1/search','a91d4c7e2b8f5036','HTTP/1.1','JSON','JSON','10.20.3.24',48712,'10.20.16.21',8082,503,'路由失败',3021,'2026-10-30 15:41:08','2026-10-30 15:41:11',
'GET /api/v1/search?keyword=gateway HTTP/1.1\nHost: api-gw.example.com\nContent-Type: application/json;charset=UTF-8\nX-AppCode: app-search\nX-Request-Id: a91d4c7e2b8f5036\nX-Forwarded-For: 10.20.3.24\nAuthorization: Bearer eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.a91d4c7e\nUser-Agent: Mozilla/5.0\nContent-Length: 132\nAccept-Encoding: gzip, deflate',
'HTTP/1.1 503 Service Unavailable\nContent-Type: application/json;charset=UTF-8\nContent-Length: 96\nX-Response-Time: 3021ms\nX-Gateway-Node: gw-node-03\nX-Upstream-Host: -\nX-Trace-Id: a91d4c7e2b8f5036\nDate: Fri, 30 Oct 2026 07:41:11 GMT',
'{\n  "appCode": "app-search",\n  "keyword": "gateway",\n  "page": 1\n}',
'{\n  "code": 50300,\n  "message": "NO_AVAILABLE_ROUTE：未匹配到可用路由或实例"\n}',
132, 96),

('app-pay','支付中心','pay-service','POST /api/v1/pay/confirm','3b7e5f1c9d2a8460','gRPC','Protobuf','Protobuf','10.20.3.31',53408,'10.20.16.33',8084,429,'限流拒绝',42,'2026-10-30 15:40:52','2026-10-30 15:40:52',
'POST /api/v1/pay/confirm HTTP/2\nHost: api-gw.example.com\nContent-Type: application/grpc+proto\nX-AppCode: app-pay\nX-Request-Id: 3b7e5f1c9d2a8460\nX-Forwarded-For: 10.20.3.31\nAuthorization: Bearer eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.3b7e5f1c\nUser-Agent: grpc-java/1.60.0\nContent-Length: 118',
'HTTP/2 429 Too Many Requests\nContent-Type: application/grpc+proto\nContent-Length: 92\nX-Response-Time: 42ms\nX-Gateway-Node: gw-node-05\nX-Upstream-Host: 10.20.16.33:8084\nX-Trace-Id: 3b7e5f1c9d2a8460\nDate: Fri, 30 Oct 2026 07:40:52 GMT',
'{\n  "appCode": "app-pay",\n  "payNo": "PAY20261030001498"\n}',
'{\n  "code": 42900,\n  "message": "RATE_LIMITED：已超过配额 800 QPS"\n}',
118, 92),

('app-file','文件服务','file-service','POST /api/v1/file/upload','c4e8a2d6f1b93705','HTTP/1.1','multipart/form-data','JSON','10.20.3.42',51276,'10.20.16.12',8083,403,'鉴权失败',68,'2026-10-30 15:39:46','2026-10-30 15:39:46',
'POST /api/v1/file/upload HTTP/1.1\nHost: api-gw.example.com\nContent-Type: multipart/form-data; boundary=----WebKitFormBoundary7MA4YWxk\nX-AppCode: app-file\nX-Request-Id: c4e8a2d6f1b93705\nX-Forwarded-For: 10.20.3.42\nAuthorization: Bearer eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.c4e8a2d6\nUser-Agent: axios/1.6.8\nContent-Length: 1048576',
'HTTP/1.1 403 Forbidden\nContent-Type: application/json;charset=UTF-8\nContent-Length: 128\nX-Response-Time: 68ms\nX-Gateway-Node: gw-node-02\nX-Upstream-Host: -\nX-Trace-Id: c4e8a2d6f1b93705\nDate: Fri, 30 Oct 2026 07:39:46 GMT',
'{\n  "appCode": "app-file",\n  "fileName": "contract.pdf"\n}',
'{\n  "code": 40301,\n  "message": "AUTH_FAILED：签名无效或 Token 已过期"\n}',
1048576, 128),

('app-user','用户中心','user-service','GET /api/v1/user/profile','5d1f7b3a8c6e2049','HTTP/1.1','JSON','JSON','10.20.3.55',49823,'10.20.16.18',8081,200,'无',54,'2026-10-30 15:38:31','2026-10-30 15:38:31',
'GET /api/v1/user/profile HTTP/1.1\nHost: api-gw.example.com\nContent-Type: application/json;charset=UTF-8\nX-AppCode: app-user\nX-Request-Id: 5d1f7b3a8c6e2049\nX-Forwarded-For: 10.20.3.55\nAuthorization: Bearer eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.5d1f7b3a\nUser-Agent: Mozilla/5.0\nContent-Length: 86',
'HTTP/1.1 200 OK\nContent-Type: application/json;charset=UTF-8\nContent-Length: 142\nX-Response-Time: 54ms\nX-Gateway-Node: gw-node-01\nX-Upstream-Host: 10.20.16.18:8081\nX-Trace-Id: 5d1f7b3a8c6e2049\nDate: Fri, 30 Oct 2026 07:38:31 GMT',
'{\n  "appCode": "app-user"\n}',
'{\n  "code": 0,\n  "data": { "userId": 100238, "level": 3 },\n  "message": "ok"\n}',
86, 142),

('app-msg','消息通知','msg-service','POST /api/v1/msg/send','8e3c9a5b7f1d6042','HTTP/1.1','JSON','JSON','10.20.3.61',52617,'10.20.16.26',8085,504,'上游响应超时',30000,'2026-10-30 15:37:12','2026-10-30 15:37:42',
'POST /api/v1/msg/send HTTP/1.1\nHost: api-gw.example.com\nContent-Type: application/json;charset=UTF-8\nX-AppCode: app-msg\nX-Request-Id: 8e3c9a5b7f1d6042\nX-Forwarded-For: 10.20.3.61\nAuthorization: Bearer eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.8e3c9a5b\nUser-Agent: okhttp/4.12.0\nContent-Length: 264',
'HTTP/1.1 504 Gateway Timeout\nContent-Type: application/json;charset=UTF-8\nContent-Length: 118\nX-Response-Time: 30000ms\nX-Gateway-Node: gw-node-06\nX-Upstream-Host: 10.20.16.26:8085\nX-Trace-Id: 8e3c9a5b7f1d6042\nDate: Fri, 30 Oct 2026 07:37:42 GMT',
'{\n  "appCode": "app-msg",\n  "to": "138****2210",\n  "templateId": "SMS_88231"\n}',
'{\n  "code": 50400,\n  "message": "UPSTREAM_TIMEOUT：上游 30s 内未响应"\n}',
264, 118),

('app-auth','统一认证','auth-service','POST /api/v1/oauth/token','2a6d8f4b1c9e7035','HTTP/1.1','x-www-form-urlencoded','JSON','10.20.3.72',47392,'10.20.16.3',8443,200,'无',96,'2026-10-30 15:36:04','2026-10-30 15:36:04',
'POST /api/v1/oauth/token HTTP/1.1\nHost: api-gw.example.com\nContent-Type: application/x-www-form-urlencoded\nX-AppCode: app-auth\nX-Request-Id: 2a6d8f4b1c9e7035\nX-Forwarded-For: 10.20.3.72\nAuthorization: Basic YXBwLWF1dGg6c2VjcmV0\nUser-Agent: curl/8.4.0\nContent-Length: 164',
'HTTP/1.1 200 OK\nContent-Type: application/json;charset=UTF-8\nContent-Length: 288\nX-Response-Time: 96ms\nX-Gateway-Node: gw-node-04\nX-Upstream-Host: 10.20.16.3:8443\nX-Trace-Id: 2a6d8f4b1c9e7035\nDate: Fri, 30 Oct 2026 07:36:04 GMT',
'grant_type=client_credentials&scope=gateway.read',
'{\n  "access_token": "eyJhbGciOiJSUzI1NiJ9.2a6d8f4b",\n  "token_type": "Bearer",\n  "expires_in": 7200\n}',
164, 288),

('app-order','订单中心','order-service','GET /api/v1/order/detail','9f4b2e8c5a1d0376','gRPC','Protobuf','Protobuf','10.20.3.11',54051,'10.20.16.9',8080,200,'无',38,'2026-10-30 15:35:22','2026-10-30 15:35:22',
'POST /api/v1/order/detail HTTP/2\nHost: api-gw.example.com\nContent-Type: application/grpc+proto\nX-AppCode: app-order\nX-Request-Id: 9f4b2e8c5a1d0376\nX-Forwarded-For: 10.20.3.11\nAuthorization: Bearer eyJhbGciOiJSUzI1NiJ9.9f4b2e8c\nUser-Agent: grpc-java/1.60.0\nContent-Length: 108',
'HTTP/2 200 OK\nContent-Type: application/grpc+proto\nContent-Length: 216\nX-Response-Time: 38ms\nX-Gateway-Node: gw-node-07\nX-Upstream-Host: 10.20.16.9:8080\nX-Trace-Id: 9f4b2e8c5a1d0376\nDate: Fri, 30 Oct 2026 07:35:22 GMT',
'{\n  "appCode": "app-order",\n  "orderNo": "SO20261030001542"\n}',
'{\n  "code": 0,\n  "data": { "status": "PAID", "amount": 299.00 }\n}',
108, 216),

('app-inventory','库存中心','inventory-service','GET /api/v1/inventory/query','7c5e1a9d3b8f2046','HTTP/1.1','JSON','JSON','10.20.3.83',50984,'10.20.16.35',8086,200,'无',62,'2026-10-30 15:34:09','2026-10-30 15:34:09',
'GET /api/v1/inventory/query?skuId=SKU-88231 HTTP/1.1\nHost: api-gw.example.com\nContent-Type: application/json;charset=UTF-8\nX-AppCode: app-inventory\nX-Request-Id: 7c5e1a9d3b8f2046\nX-Forwarded-For: 10.20.3.83\nAuthorization: Bearer eyJhbGciOiJSUzI1NiJ9.7c5e1a9d\nUser-Agent: Mozilla/5.0\nContent-Length: 124',
'HTTP/1.1 200 OK\nContent-Type: application/json;charset=UTF-8\nContent-Length: 186\nX-Response-Time: 62ms\nX-Gateway-Node: gw-node-05\nX-Upstream-Host: 10.20.16.35:8086\nX-Trace-Id: 7c5e1a9d3b8f2046\nDate: Fri, 30 Oct 2026 07:34:09 GMT',
'{\n  "appCode": "app-inventory",\n  "skuId": "SKU-88231"\n}',
'{\n  "code": 0,\n  "data": { "skuId": "SKU-88231", "stock": 1428 }\n}',
124, 186),

('app-coupon','优惠券中心','coupon-service','POST /api/v1/coupon/use','b3f7d9e1a5c80624','HTTP/1.1','JSON','JSON','10.20.3.94',48265,'10.20.16.41',8087,400,'参数校验失败',46,'2026-10-30 15:33:15','2026-10-30 15:33:15',
'POST /api/v1/coupon/use HTTP/1.1\nHost: api-gw.example.com\nContent-Type: application/json;charset=UTF-8\nX-AppCode: app-coupon\nX-Request-Id: b3f7d9e1a5c80624\nX-Forwarded-For: 10.20.3.94\nAuthorization: Bearer eyJhbGciOiJSUzI1NiJ9.b3f7d9e1\nUser-Agent: okhttp/4.12.0\nContent-Length: 342',
'HTTP/1.1 400 Bad Request\nContent-Type: application/json;charset=UTF-8\nContent-Length: 152\nX-Response-Time: 46ms\nX-Gateway-Node: gw-node-02\nX-Upstream-Host: 10.20.16.41:8087\nX-Trace-Id: b3f7d9e1a5c80624\nDate: Fri, 30 Oct 2026 07:33:15 GMT',
'{\n  "appCode": "app-coupon",\n  "couponId": "",\n  "orderNo": "SO20261030001542"\n}',
'{\n  "code": 40001,\n  "message": "INVALID_PARAM：couponId 不能为空"\n}',
342, 152),

('app-settle','结算中心','settle-service','GET /api/v1/settle/bill','e8a1c6f3d9b20475','HTTP/1.1','JSON','JSON','10.20.3.101',53307,'10.20.16.44',8088,200,'无',128,'2026-10-30 15:32:03','2026-10-30 15:32:03',
'GET /api/v1/settle/bill?month=2026-10 HTTP/1.1\nHost: api-gw.example.com\nContent-Type: application/json;charset=UTF-8\nX-AppCode: app-settle\nX-Request-Id: e8a1c6f3d9b20475\nX-Forwarded-For: 10.20.3.101\nAuthorization: Bearer eyJhbGciOiJSUzI1NiJ9.e8a1c6f3\nUser-Agent: Mozilla/5.0\nContent-Length: 96',
'HTTP/1.1 200 OK\nContent-Type: application/json;charset=UTF-8\nContent-Length: 364\nX-Response-Time: 128ms\nX-Gateway-Node: gw-node-03\nX-Upstream-Host: 10.20.16.44:8088\nX-Trace-Id: e8a1c6f3d9b20475\nDate: Fri, 30 Oct 2026 07:32:03 GMT',
'{\n  "appCode": "app-settle",\n  "month": "2026-10"\n}',
'{\n  "code": 0,\n  "data": { "billId": "B20261031001", "total": 128460.50 }\n}',
96, 364),

('app-report','报表中心','report-service','POST /api/v1/report/export','4c9e2b7f8a1d6035','HTTP/1.1','JSON','JSON','10.20.3.112',49518,'10.20.16.49',8089,502,'上游响应异常',8120,'2026-10-30 15:31:27','2026-10-30 15:31:35',
'POST /api/v1/report/export HTTP/1.1\nHost: api-gw.example.com\nContent-Type: application/json;charset=UTF-8\nX-AppCode: app-report\nX-Request-Id: 4c9e2b7f8a1d6035\nX-Forwarded-For: 10.20.3.112\nAuthorization: Bearer eyJhbGciOiJSUzI1NiJ9.4c9e2b7f\nUser-Agent: okhttp/4.12.0\nContent-Length: 152',
'HTTP/1.1 502 Bad Gateway\nContent-Type: application/json;charset=UTF-8\nContent-Length: 128\nX-Response-Time: 8120ms\nX-Gateway-Node: gw-node-06\nX-Upstream-Host: 10.20.16.49:8089\nX-Trace-Id: 4c9e2b7f8a1d6035\nDate: Fri, 30 Oct 2026 07:31:35 GMT',
'{\n  "appCode": "app-report",\n  "range": "2026-09" }',
'{\n  "code": 50200,\n  "message": "UPSTREAM_BAD_RESPONSE：上游返回非法响应"\n}',
152, 128),

('app-open','开放平台','open-service','GET /api/v1/open/apps','6a2f4c8e1b9d3057','HTTP/1.1','JSON','JSON','10.20.3.121',52140,'10.20.16.52',9000,200,'无',74,'2026-10-30 15:30:18','2026-10-30 15:30:18',
'GET /api/v1/open/apps HTTP/1.1\nHost: api-gw.example.com\nContent-Type: application/json;charset=UTF-8\nX-AppCode: app-open\nX-Request-Id: 6a2f4c8e1b9d3057\nX-Forwarded-For: 10.20.3.121\nAuthorization: Bearer eyJhbGciOiJSUzI1NiJ9.6a2f4c8e\nUser-Agent: axios/1.6.8\nContent-Length: 88',
'HTTP/1.1 200 OK\nContent-Type: application/json;charset=UTF-8\nContent-Length: 292\nX-Response-Time: 74ms\nX-Gateway-Node: gw-node-01\nX-Upstream-Host: 10.20.16.52:9000\nX-Trace-Id: 6a2f4c8e1b9d3057\nDate: Fri, 30 Oct 2026 07:30:18 GMT',
'{\n  "appCode": "app-open"\n}',
'{\n  "code": 0,\n  "data": { "total": 12, "apps": [] }\n}',
88, 292),

('app-user','用户中心','user-profile-service','PUT /api/v1/user/settings','1b8d5f2a9c4e7063','HTTP/1.1','JSON','JSON','10.20.3.55',51207,'10.20.16.19',8091,200,'无',88,'2026-10-30 15:29:41','2026-10-30 15:29:41',
'PUT /api/v1/user/settings HTTP/1.1\nHost: api-gw.example.com\nContent-Type: application/json;charset=UTF-8\nX-AppCode: app-user\nX-Request-Id: 1b8d5f2a9c4e7063\nX-Forwarded-For: 10.20.3.55\nAuthorization: Bearer eyJhbGciOiJSUzI1NiJ9.1b8d5f2a\nUser-Agent: Mozilla/5.0\nContent-Length: 176',
'HTTP/1.1 200 OK\nContent-Type: application/json;charset=UTF-8\nContent-Length: 132\nX-Response-Time: 88ms\nX-Gateway-Node: gw-node-05\nX-Upstream-Host: 10.20.16.19:8091\nX-Trace-Id: 1b8d5f2a9c4e7063\nDate: Fri, 30 Oct 2026 07:29:41 GMT',
'{\n  "theme": "dark",\n  "notify": true\n}',
'{\n  "code": 0,\n  "message": "ok"\n}',
176, 132);

-- ----------------------------------------------------------------------------
-- 网关转发路由：业务方在控制台「路由配置」维护，proxy 模块每 30 秒热加载
-- ----------------------------------------------------------------------------
DROP TABLE IF EXISTS tb_gw_route;
CREATE TABLE tb_gw_route (
  id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  app_id        VARCHAR(64)  NOT NULL                COMMENT '归属应用编码',
  app_name      VARCHAR(64)           DEFAULT NULL   COMMENT '归属应用名称',
  service_name  VARCHAR(64)           DEFAULT NULL   COMMENT '命中的服务名',
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

INSERT INTO tb_gw_route (app_id, app_name, service_name, path_prefix, upstream_url, prefix_path, strip_prefix, status, remark) VALUES
('app-gateway', '网关平台', 'jv-gateway-discovery', '/gw-demo/**', 'http://127.0.0.1:28083', '/api', 1, 1, '自测路由：转发到服务发现接口'),
('app-gateway', 'API网关平台', 'jv-gateway-user',      '/api/auth/**',      'http://127.0.0.1:28081', NULL, 0, 1, '平台自身：认证服务'),
('app-gateway', 'API网关平台', 'jv-gateway-user',      '/api/users/**',     'http://127.0.0.1:28081', NULL, 0, 1, '平台自身：用户管理'),
('app-gateway', 'API网关平台', 'jv-gateway-overview',  '/api/overview/**',  'http://127.0.0.1:28082', NULL, 0, 1, '平台自身：总览统计'),
('app-gateway', 'API网关平台', 'jv-gateway-overview',  '/api/stats/**',     'http://127.0.0.1:28082', NULL, 0, 1, '平台自身：数据统计'),
('app-gateway', 'API网关平台', 'jv-gateway-discovery', '/api/discovery/**', 'http://127.0.0.1:28083', NULL, 0, 1, '平台自身：服务发现'),
('app-gateway', 'API网关平台', 'jv-gateway-trace',     '/api/trace/**',     'http://127.0.0.1:28084', NULL, 0, 1, '平台自身：日志上报');

-- 默认管理员：admin / Admin@123（首次登录后请立即修改）
INSERT INTO tb_sys_user (username, password, nickname, email, phone, role, status, token_version, deleted) VALUES
('admin', '$2a$10$Uv8R5rQN79vut4LRnQjdcOtSyo8yoWN3aSvadEpktLVIpjzEAfPHO', '平台管理员', 'admin@example.com', '13000000000', 'ADMIN', 1, 0, 0);
