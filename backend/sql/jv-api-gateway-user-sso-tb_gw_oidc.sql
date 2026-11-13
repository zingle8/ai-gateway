-- =============================================================
--  jv-api-gateway-user | 单点登录 SSO（OIDC Provider，v0.2 第 15 项）
--
--  tb_gw_oidc_client 接入方（业务平台 SP）登记：client_id / client_secret（SHA-256）/ 回调地址 /
--                    关联开放平台 appId（账号-平台授权校验，第 7 项）
--  tb_gw_sso_log     SSO 审计：授权 / 换 token / userinfo 各记一条（谁、哪个接入方、结果、来源 IP）
--
--  说明：client_secret 原文只在签发时返回一次，库内只存 SHA-256 摘要（与 API Key 同口径）。
--  本脚本由 jv-api-gateway-user 启动时幂等执行（PermSchemaInitializer）。
-- =============================================================

CREATE TABLE IF NOT EXISTS tb_gw_oidc_client (
  id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  client_id     VARCHAR(64)  NOT NULL                COMMENT '客户端标识 gwsp-xxxxxxxx',
  client_secret VARCHAR(128) NOT NULL                COMMENT 'SHA-256 十六进制摘要',
  client_name   VARCHAR(128) NOT NULL                COMMENT '接入方名称',
  app_id        VARCHAR(64)  DEFAULT NULL            COMMENT '关联开放平台 appId（授权校验用）',
  redirect_uri  VARCHAR(500) NOT NULL                COMMENT '回调地址，多个逗号分隔（精确匹配）',
  scopes        VARCHAR(128) DEFAULT 'openid profile' COMMENT '授权范围',
  status        TINYINT      NOT NULL DEFAULT 1      COMMENT '1 启用 0 停用',
  create_time   DATETIME     DEFAULT CURRENT_TIMESTAMP,
  update_time   DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_client_id (client_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'SSO 接入方（OIDC Client）登记';

CREATE TABLE IF NOT EXISTS tb_gw_sso_log (
  id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  user_id     BIGINT       DEFAULT NULL            COMMENT '用户 ID（未登录授权时为空）',
  username    VARCHAR(64)  DEFAULT NULL            COMMENT '账号',
  client_id   VARCHAR(64)  DEFAULT NULL            COMMENT '接入方 client_id',
  app_id      VARCHAR(64)  DEFAULT NULL            COMMENT '关联平台 appId',
  action      VARCHAR(16)  NOT NULL                COMMENT 'AUTHORIZE / TOKEN / USERINFO / LOGOUT',
  result      VARCHAR(16)  NOT NULL                COMMENT 'SUCCESS / DENIED / ERROR',
  ip          VARCHAR(64)  DEFAULT NULL            COMMENT '来源 IP',
  detail      VARCHAR(255) DEFAULT NULL            COMMENT '说明',
  create_time DATETIME     NOT NULL                COMMENT '时间',
  PRIMARY KEY (id),
  KEY idx_time (create_time),
  KEY idx_client (client_id, create_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'SSO 登录审计';
