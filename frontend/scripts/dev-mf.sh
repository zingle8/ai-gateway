#!/usr/bin/env bash
# =============================================================
# 微前端联调启动脚本（v0.2 第 16 项）
#   8 个模块：各自独立构建后用 vite preview 托管在地址表分配的端口（28091–28098），
#             remoteEntry 复制到 dist 根（其内部按 ./assets/ 引用 chunk，必须从站点根加载）；
#   宿主 portal：VITE_MF=true 的 dev server（28080），按地址表动态加载各 remote。
#
#   为什么不是「模块 dev server 直连」：@originjs/vite-plugin-federation 1.3.x 在
#   Vite 5 的 dev server 下不产出 remoteEntry（构建期才生成），因此模块侧统一走
#   build + preview；改模块代码后重跑本脚本即可（或自行 vite build --watch）。
#
# 用法：bash frontend/scripts/dev-mf.sh [模块名...]   # 不带参数启动全部
# =============================================================
set -e
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

MODULES=(overview stats discovery trace auth onboard perm system)
if [ $# -gt 0 ]; then
  MODULES=("$@")
fi

PIDS=()
cleanup() {
  echo ""
  echo "[dev-mf] 停止全部子进程..."
  for pid in "${PIDS[@]:-}"; do
    kill "$pid" 2>/dev/null || true
  done
}
trap cleanup EXIT INT TERM

for m in "${MODULES[@]}"; do
  echo "[dev-mf] 构建模块 $m ..."
  (cd "modules/$m" && npx vite build --config vite.config.js >/tmp/mf-build-$m.log 2>&1)
  cp "modules/$m/dist/assets/remoteEntry.js" "modules/$m/dist/remoteEntry.js"
  cp modules/$m/dist/assets/style-*.css "modules/$m/dist/" 2>/dev/null || true
  PORT=$(node -e "console.log(require('./mf-manifest.json').modules['$m'].devPort)")
  echo "[dev-mf] 启动模块 $m preview :$PORT"
  (cd "modules/$m" && npx vite preview --config vite.config.js --port "$PORT" --strictPort) &
  PIDS+=($!)
done

echo "[dev-mf] 启动宿主 portal（28080，联邦模式）..."
VITE_MF=true npx vite --config apps/portal/vite.config.js &
PIDS+=($!)

wait
