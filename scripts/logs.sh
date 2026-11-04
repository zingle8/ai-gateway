#!/usr/bin/env bash
# AI 网关平台 —— 查看日志
#
# 用法:
#   ./logs.sh                # 跟踪全部服务日志
#   ./logs.sh jv-gateway-user   # 只跟踪指定服务
#   ./logs.sh -n 100         # 只看最近 100 行（不跟踪）
#
# 可选服务名: jv-gateway-user / jv-gateway-overview / jv-gateway-discovery /
#             jv-gateway-trace / gateway-portal

set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=_common.sh
source "$SCRIPT_DIR/_common.sh"

FOLLOW=true
LINES=200
TARGET=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    -n) LINES="$2"; FOLLOW=false; shift 2 ;;
    -h|--help) sed -n '2,14p' "${BASH_SOURCE[0]}"; exit 0 ;;
    *) TARGET="$1"; shift ;;
  esac
done

AVAILABLE_NAMES=()
for svc in "${BACKEND_SERVICES[@]}"; do
  IFS='|' read -r name _ <<<"$svc"
  AVAILABLE_NAMES+=("$name")
done
AVAILABLE_NAMES+=("$FRONTEND_NAME")

if [[ -n "$TARGET" ]]; then
  if [[ ! -f "$(log_file "$TARGET")" ]]; then
    log_err "未知服务或尚未产生日志：$TARGET"
    printf '可选: %s\n' "${AVAILABLE_NAMES[*]}"
    exit 1
  fi
  if [[ "$FOLLOW" == "true" ]]; then
    exec tail -f "$(log_file "$TARGET")"
  else
    exec tail -n "$LINES" "$(log_file "$TARGET")"
  fi
fi

# 全部服务
EXISTING=()
for n in "${AVAILABLE_NAMES[@]}"; do
  [[ -f "$(log_file "$n")" ]] && EXISTING+=("$(log_file "$n")")
done

if [[ ${#EXISTING[@]} -eq 0 ]]; then
  log_warn "暂无日志文件，请先执行 ./start.sh"
  exit 0
fi

if [[ "$FOLLOW" == "true" ]]; then
  exec tail -f "${EXISTING[@]}"
else
  exec tail -n "$LINES" "${EXISTING[@]}"
fi
