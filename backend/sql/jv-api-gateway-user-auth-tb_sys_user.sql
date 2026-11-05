-- =============================================================================
-- jv-api-gateway-user | 账号认证 | tb_sys_user
--
-- 平台账号表。由 UserSchemaInitializer 启动时自动执行（幂等），
-- 账号数据由 AdminAccountInitializer 首次启动写入。
-- =============================================================================
USE api_gateway;

CREATE TABLE IF NOT EXISTS tb_sys_user (
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
