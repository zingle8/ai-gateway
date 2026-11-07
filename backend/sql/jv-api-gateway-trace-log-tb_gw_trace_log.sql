-- =============================================================================
-- jv-api-gateway-trace | 调用日志 | tb_gw_trace_log
--
-- 网关调用日志（含演示数据）。线上由 proxy 的 TraceGlobalFilter 实时写入。
-- =============================================================================
USE api_gateway;

-- ----------------------------
-- 4. 网关调用日志表（日志追踪）
-- ----------------------------

CREATE TABLE IF NOT EXISTS tb_gw_trace_log (
  id           BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
  app_id       VARCHAR(64)           DEFAULT NULL   COMMENT '应用编码',
  app_name     VARCHAR(64)           DEFAULT NULL   COMMENT '应用名称',
  service_name VARCHAR(64)           DEFAULT NULL   COMMENT '服务ID（英文唯一标识，上报归属列）',
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
  KEY idx_reqtime (req_time),
  KEY idx_time_cost (req_time, cost)                      COMMENT '分位数/耗时排序：覆盖索引免回表',
  KEY idx_svc_cover (service_name, req_time, cost, req_bytes, res_bytes) COMMENT '服务维度区间聚合：覆盖索引免回表'
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '网关调用日志';

-- ============================== 演示数据 ==============================
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
