#!/usr/bin/env bash
# AI 网关平台 —— 初始化数据库
#
# 用法:
#   ./init-db.sh              # 建库并执行 backend/sql/api_gateway.sql
#   ./init-db.sh --drop       # 先删除已存在的 api_gateway 库再重建（会丢数据）
#
# 可通过环境变量覆盖连接信息:
#   DB_HOST / DB_PORT / DB_USER / DB_PASSWORD / DB_NAME

set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=_common.sh
source "$SCRIPT_DIR/_common.sh"

SQL_FILE="$BACKEND_DIR/sql/api_gateway.sql"

title "初始化数据库"

if [[ ! -f "$SQL_FILE" ]]; then
  log_err "未找到建表脚本：$SQL_FILE"
  exit 1
fi

if ! command -v mysql >/dev/null 2>&1; then
  log_err "未找到 mysql 客户端，请先安装或改用手动执行："
  printf '  docker exec -i mysql57 mysql -uroot -p%s < %s\n' "$DB_PASSWORD" "$SQL_FILE"
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

log_info "执行建表脚本 $SQL_FILE ..."
mysql_exec "$DB_NAME" <"$SQL_FILE" || {
  log_err "建表失败"
  exit 1
}

log_ok "数据库初始化完成"
printf '  库名: %s\n' "$DB_NAME"
mysql_exec -e "USE \`$DB_NAME\`; SHOW TABLES;"
