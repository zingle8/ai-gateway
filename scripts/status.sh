#!/usr/bin/env bash
# AI 网关平台 —— 查看运行状态
#
# 用法: ./status.sh

set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=_common.sh
source "$SCRIPT_DIR/_common.sh"

printf '%s\n' "AI 网关平台运行状态"
printf '%s\n' "---------------------------------------------------------------------"
printf '  %-24s %-6s %-10s %s\n' "服务" "端口" "状态" "地址"
printf '%s\n' "---------------------------------------------------------------------"

show() {
  local name="$1" port="$2" path="${3:-/api}"
  local pid status color
  pid="$(read_pid "$name" || true)"
  if [[ -n "$pid" ]]; then
    status="运行中($pid)"
    color="$C_GREEN"
  elif port_in_use "$port"; then
    status="端口占用"
    color="$C_YELLOW"
  else
    status="已停止"
    color="$C_RED"
  fi
  printf '  %-24s %-6s %s%-12s%s %s\n' \
    "$name" "$port" "$color" "$status" "$C_OFF" "http://127.0.0.1:$port$path"
}

for svc in "${BACKEND_SERVICES[@]}"; do
  IFS='|' read -r name port <<<"$svc"
  show "$name" "$port" "/api"
done
show "$FRONTEND_NAME" "$FRONTEND_PORT" "/"

printf '%s\n' "---------------------------------------------------------------------"
printf '  日志目录: %s\n' "$LOG_DIR"
printf '  实时跟踪: %s/logs.sh [服务名]\n' "$(cd "$SCRIPT_DIR" && pwd)"
