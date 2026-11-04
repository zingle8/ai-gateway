#!/usr/bin/env bash
# AI 网关平台 —— 一键关闭
#
# 用法:
#   ./stop.sh              # 关闭全部
#   ./stop.sh backend      # 只关闭后端
#   ./stop.sh frontend     # 只关闭前端
#
# 停止策略：优先按 logs/*.pid 发 TERM 优雅退出，15s 未退出则 KILL；
# 无 PID 文件时按端口兜底清理（仅清理 gateway-*.jar / vite 进程，不误杀其他进程）。

set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=_common.sh
source "$SCRIPT_DIR/_common.sh"

TARGET="${1:-all}"

stop_one() {
  local name="$1" port="$2"
  local pid
  pid="$(read_pid "$name" || true)"

  if [[ -z "$pid" ]] && ! port_in_use "$port"; then
    log_info "$name 未在运行"
    return 0
  fi

  if [[ -n "$pid" ]]; then
    log_info "停止 $name (pid=$pid)..."
    terminate_pid "$pid" 15
    rm -f "$(pid_file "$name")"
  fi

  # 兜底：清理端口上的残留进程
  kill_port_owner "$port"

  if port_in_use "$port"; then
    log_err "$name 仍未释放端口 $port"
    return 1
  fi
  log_ok "$name 已停止"
}

stop_backend() {
  title "关闭后端微服务"
  local failed=0
  for svc in "${BACKEND_SERVICES[@]}"; do
    IFS='|' read -r name port <<<"$svc"
    stop_one "$name" "$port" || failed=$((failed + 1))
  done
  (( failed > 0 )) && return 1
  return 0
}

stop_frontend() {
  title "关闭前端主应用"
  stop_one "$FRONTEND_NAME" "$FRONTEND_PORT"
}

case "$TARGET" in
  all)      stop_frontend; stop_backend ;;
  backend)  stop_backend ;;
  frontend) stop_frontend ;;
  -h|--help) sed -n '2,14p' "${BASH_SOURCE[0]}"; exit 0 ;;
  *)        log_err "未知参数：$TARGET"; echo "用法: $0 [all|backend|frontend]"; exit 1 ;;
esac
