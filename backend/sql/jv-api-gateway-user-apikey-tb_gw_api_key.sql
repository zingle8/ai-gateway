-- =============================================================================
-- jv-api-gateway-user | 开放令牌 | tb_gw_api_key
--
-- 网关 API Key：只存 SHA-256(secret)，明文仅签发/轮换时返回一次。
-- 由 PermSchemaInitializer 启动时自动执行（幂等）。
-- =============================================================================
USE api_gateway;

CREATE TABLE IF NOT EXISTS tb_gw_api_key (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  key_id        VARCHAR(32)  NOT NULL,
  secret_hash   CHAR(64)     NOT NULL,
  name          VARCHAR(64)  NOT NULL,
  owner_user_id BIGINT       NULL,
  status        TINYINT      NOT NULL DEFAULT 1,
  expire_at     DATETIME     NULL,
  last_used_at  DATETIME     NULL,
  last_used_ip  VARCHAR(64)  NULL,
  create_time   DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time   DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_key_id (key_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='网关 API Key';
