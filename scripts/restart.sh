#!/usr/bin/env bash
# AI 网关平台 —— 一键重启
#
# 用法:
#   ./restart.sh              # 重启全部
#   ./restart.sh backend      # 只重启后端
#   ./restart.sh frontend     # 只重启前端
#   ./restart.sh all --build  # 重新构建后端后再重启
#
# 等价于 stop.sh <target> && start.sh <target> [args...]

set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

TARGET="${1:-all}"

if [[ "$TARGET" == "-h" || "$TARGET" == "--help" ]]; then
  sed -n '2,16p' "${BASH_SOURCE[0]}"
  exit 0
fi

case "$TARGET" in
  all|backend|frontend) ;;
  *) echo "用法: $0 [all|backend|frontend] [--build]"; exit 1 ;;
esac

# 通过 bash 显式调用，不依赖脚本自身的执行位
bash "$SCRIPT_DIR/stop.sh" "$TARGET"

# 等端口真正释放，避免刚停就起导致端口冲突
sleep 2

bash "$SCRIPT_DIR/start.sh" "$@"
