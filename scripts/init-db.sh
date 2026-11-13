#!/usr/bin/env bash
# AI 网关平台 —— 初始化数据库
#
# 用法:
#   ./init-db.sh              # 建库并执行 backend/sql 下全部脚本（幂等）
#   ./init-db.sh --drop       # 先删除已存在的 api_gateway 库再重建（会丢数据）
#
# 可通过环境变量覆盖连接信息:
#   DB_HOST / DB_PORT / DB_USER / DB_PASSWORD / DB_NAME

set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=_common.sh
source "$SCRIPT_DIR/_common.sh"

SQL_DIR="$BACKEND_DIR/sql"

title "初始化数据库"

if [[ ! -d "$SQL_DIR" ]]; then
  log_err "未找到 SQL 目录：$SQL_DIR"
  exit 1
fi

# 执行顺序：账号表 → 权限 → API Key → 路由 → 配置 → 注册表 → 调用日志 → span → 拓扑
# （脚本全部幂等，顺序仅影响依赖：权限种子依赖账号表已存在）
SQL_FILES=(
  "$SQL_DIR/jv-api-gateway-user-auth-tb_sys_user.sql"
  "$SQL_DIR/jv-api-gateway-user-perm-tb_perm_group.sql"
  "$SQL_DIR/jv-api-gateway-user-apikey-tb_gw_api_key.sql"
  "$SQL_DIR/jv-api-gateway-proxy-route-tb_gw_route.sql"
  "$SQL_DIR/jv-api-gateway-config-config-tb_sys_config.sql"
  "$SQL_DIR/jv-api-gateway-discovery-registry-tb_gw_service.sql"
  "$SQL_DIR/jv-api-gateway-discovery-host-metrics.sql"
  "$SQL_DIR/jv-api-gateway-trace-log-tb_gw_trace_log.sql"
  "$SQL_DIR/jv-api-gateway-trace-span-tb_gw_trace_span.sql"
  "$SQL_DIR/jv-api-gateway-trace-span-tb_gw_trace_span-partition.sql"
  "$SQL_DIR/jv-api-gateway-trace-topology-tb_gw_service_edge.sql"
  "$SQL_DIR/jv-api-gateway-scheduler-tables.sql"
)

if ! command -v mysql >/dev/null 2>&1; then
  log_err "未找到 mysql 客户端，请先安装或改用手动执行："
  printf '  docker exec -i mysql57 mysql -uroot -p%s < %s\n' "$DB_PASSWORD" "$SQL_DIR/jv-api-gateway-user-auth-tb_sys_user.sql"
  exit 1
fi

if ! port_in_use "$DB_PORT"; then
  log_err "MySQL ($DB_HOST:$DB_PORT) 不可达，请先启动容器：docker start mysql57"
  exit 1
fi

if [[ "${1:-}" == "--drop" ]]; then
  log_warn "删除数据库 $DB_NAME ..."
  mysql_exec -e "DROP DATABASE IF EXISTS \`$DB_NAME\`;" || exit 1
fi

log_info "创建数据库 $DB_NAME ..."
mysql_exec -e "CREATE DATABASE IF NOT EXISTS \`$DB_NAME\` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;" || exit 1

for sql in "${SQL_FILES[@]}"; do
  [[ -f "$sql" ]] || { log_warn "跳过不存在的脚本：$(basename "$sql")"; continue; }
  log_info "执行 $(basename "$sql") ..."
  mysql_exec "$DB_NAME" <"$sql" || {
    log_err "执行失败：$(basename "$sql")"
    exit 1
  }
done

log_ok "数据库初始化完成"
printf '  库名: %s\n' "$DB_NAME"
mysql_exec -e "USE \`$DB_NAME\`; SHOW TABLES;"
