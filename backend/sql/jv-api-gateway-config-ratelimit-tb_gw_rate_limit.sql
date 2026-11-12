-- =============================================================================
-- jv-api-gateway-config | 网关限流 | tb_gw_rate_limit
--
-- 限流规则由控制台「接入中心 → 限流策略」维护（config 服务读写），
-- 网关 proxy 以 5 秒缓存加载启用规则，按令牌桶算法放行/拒绝（Redis 优先，宕机回落本地）。
--
-- 维度 dimension：
--   GLOBAL  —— 全局限流，target 固定 '*'
--   ROUTE   —— 按请求路径匹配，target 为 Ant 风格路径（如 /gw-demo/**）
--   API_KEY —— 按调用方 API Key 限流，target 为 keyId 或 '*'
--   IP      —— 按调用方 IP 限流，target 为具体 IP 或 '*'
-- 多条规则同时生效，任一超限即返回 429。
-- =============================================================================
USE api_gateway;

CREATE TABLE IF NOT EXISTS tb_gw_rate_limit (
  id               BIGINT AUTO_INCREMENT PRIMARY KEY,
  name             VARCHAR(64)  NOT NULL,
  dimension        VARCHAR(16)  NOT NULL DEFAULT 'GLOBAL',
  target           VARCHAR(200) NOT NULL DEFAULT '*',
  replenish_rate   INT          NOT NULL DEFAULT 100,
  burst_capacity   INT          NOT NULL DEFAULT 200,
  requested_tokens INT          NOT NULL DEFAULT 1,
  status           TINYINT      NOT NULL DEFAULT 1,
  remark           VARCHAR(255) NULL,
  create_time      DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time      DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='网关限流规则（令牌桶）';

-- 种子：全局兜底 + 自测路由
INSERT INTO tb_gw_rate_limit (name, dimension, target, replenish_rate, burst_capacity, requested_tokens, status, remark)
SELECT '全局兜底', 'GLOBAL', '*', 1000, 2000, 1, 1, '平台默认保护：1000 次/秒，突发 2000'
WHERE NOT EXISTS (SELECT 1 FROM tb_gw_rate_limit WHERE dimension = 'GLOBAL' AND target = '*');

INSERT INTO tb_gw_rate_limit (name, dimension, target, replenish_rate, burst_capacity, requested_tokens, status, remark)
SELECT '自测路由限流', 'ROUTE', '/gw-demo/**', 20, 40, 1, 1, '演示路由 20 次/秒，突发 40'
WHERE NOT EXISTS (SELECT 1 FROM tb_gw_rate_limit WHERE dimension = 'ROUTE' AND target = '/gw-demo/**');
