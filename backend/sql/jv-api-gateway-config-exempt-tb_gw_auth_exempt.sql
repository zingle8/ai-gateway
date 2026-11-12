-- =============================================================================
-- jv-api-gateway-config | 鉴权精细豁免 | tb_gw_auth_exempt
--
-- 豁免规则由控制台「鉴权豁免」页维护，网关 proxy 每 5 秒加载启用且未过期的规则：
--   LOGIN —— 跳过登录鉴权（等价于在 yml skip-paths 追加该路径）
--   RATE  —— 跳过限流（如回调 / 探活等高频通道）
--   ALL   —— 两者都跳过
-- yml 中的内置白名单仍然有效（本表是「追加」，不替换），避免误操作导致登录不可用。
-- =============================================================================
USE api_gateway;

CREATE TABLE IF NOT EXISTS tb_gw_auth_exempt (
  id           BIGINT AUTO_INCREMENT PRIMARY KEY,
  name         VARCHAR(64)  NOT NULL,
  path_pattern VARCHAR(200) NOT NULL,
  methods      VARCHAR(64)  NOT NULL DEFAULT '*',
  exempt_type  VARCHAR(16)  NOT NULL DEFAULT 'LOGIN',
  status       TINYINT      NOT NULL DEFAULT 1,
  expire_at    DATETIME     NULL,
  remark       VARCHAR(255) NULL,
  create_time  DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time  DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='鉴权 / 限流精细豁免规则';

-- 种子：一条停用示例，避免默认放行面扩大
INSERT INTO tb_gw_auth_exempt (name, path_pattern, methods, exempt_type, status, remark)
SELECT '健康检查（示例）', '/health/**', 'GET', 'LOGIN', 0, '示例规则：默认停用；/health 已在 yml 白名单中'
WHERE NOT EXISTS (SELECT 1 FROM tb_gw_auth_exempt WHERE path_pattern = '/health/**');
