#!/usr/bin/env bash
# =============================================================
# 微前端构建脚本（v0.2 第 16 项）
#   8 个模块各自独立构建（federation remote）
#   → remoteEntry 复制到模块 dist 根（其内部按 ./assets/ 引用 chunk，
#     若仍放在 assets/ 下会解析成 assets/assets/ 双重路径，404）
#   → 汇总到 portal/dist/mf/<模块名>/（remoteEntry.js + assets/）；
#   portal 以联邦模式构建（VITE_MF=true，remote 地址 = /mf/<模块>/remoteEntry.js，
#   部署期可用 VITE_MF_BASE 整体改前缀，如指向 CDN）。
# 产物可整体静态托管：portal/dist 下的 index.html + /mf/* 即完整站点。
# =============================================================
set -e
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

MODULES=(overview stats discovery trace auth onboard perm system)

# 先构建宿主：vite build 默认 emptyOutDir，会清空 portal/dist，
# 所以模块产物必须在宿主构建之后再汇总进去（顺序反过来会被清掉）
echo "[build-mf] 构建宿主 portal（联邦模式）..."
# 必须在 portal 目录内构建：vite 的 root 取 cwd，否则找不到 index.html
(cd apps/portal && VITE_MF=true npx vite build --config vite.config.js)

# 地址表随静态资源发布，宿主启动时拉取用于失败占位页展示
cp mf-manifest.json apps/portal/dist/mf-manifest.json

for m in "${MODULES[@]}"; do
  echo "[build-mf] 构建模块 $m ..."
  (cd "modules/$m" && npx vite build --config vite.config.js)
  # remoteEntry 内部对 chunk 用 './assets/...'、对样式用裸文件名，
  # 因此 remoteEntry 与 style-*.css 都要落到 dist 根，宿主才能按 /mf/<m>/ 前缀取到
  cp "modules/$m/dist/assets/remoteEntry.js" "modules/$m/dist/remoteEntry.js"
  cp modules/$m/dist/assets/style-*.css "modules/$m/dist/" 2>/dev/null || true
  rm -rf "apps/portal/dist/mf/$m"
  mkdir -p "apps/portal/dist/mf/$m"
  cp "modules/$m/dist/remoteEntry.js" "apps/portal/dist/mf/$m/"
  cp modules/$m/dist/style-*.css "apps/portal/dist/mf/$m/" 2>/dev/null || true
  cp -r "modules/$m/dist/assets" "apps/portal/dist/mf/$m/"
done

echo "[build-mf] 完成：apps/portal/dist（含 /mf/<模块>/remoteEntry.js + assets/）"
