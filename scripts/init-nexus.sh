#!/usr/bin/env bash
# AI 网关平台 —— Maven 私服（Nexus 3）一键部署
#
# 用途：SDK 跨团队 / 多项目分发（见 docs/client-integration.md 1.2 节）。
# 部署后把 dist/sdk-jar 的 5 个文件 deploy 到 maven-releases 仓库即可，
# 所有业务方声明一次仓库地址，无需再各自 install.sh。
#
# 幂等：容器已存在直接 docker start；端口已被占用视为已就绪，跳过。
#
# 用法:
#   ./init-nexus.sh            # 拉起 Nexus（端口默认 28095）
#   ./init-nexus.sh --check    # 只检查不拉起
#
# 首次使用:
#   1. 初始管理员密码：docker exec zimo-nexus cat /nexus-data/admin.password
#   2. 浏览器打开 http://127.0.0.1:28095 → 用 admin 登录 → 按提示改密码
#   3. 建一个发布凭据（齿轮 → Users → admin → Reset password，或新建账号）
#   4. 发布方 ~/.m2/settings.xml 配 <server><id>zimo-releases</id>，之后照常 deploy
#   详细步骤见 docs/maven-nexus-deployment.md
#
# 端口说明:
#   平台业务服务占 28081~28090，Nacos 占 8080/8848，因此 Nexus 用 28095，
#   与平台端口段保持一致；可用 NEXUS_PORT 环境变量覆盖。

set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=_common.sh
source "$SCRIPT_DIR/_common.sh"

NEXUS_PORT="${NEXUS_PORT:-28095}"
NEXUS_IMAGE="sonatype/nexus3:latest"
NEXUS_NAME="zimo-nexus"
# 数据卷：宿主机目录，升级镜像不丢数据
NEXUS_DATA="${NEXUS_DATA:-$ROOT_DIR/dist/nexus-data}"

CHECK_ONLY=false
[[ "${1:-}" == "--check" ]] && CHECK_ONLY=true

# ---------------------------------------------------------------- 主流程

if port_in_use "$NEXUS_PORT"; then
  log_ok "Maven 私服 (Nexus) 端口 $NEXUS_PORT 可达"
  log_info "仓库地址：http://127.0.0.1:$NEXUS_PORT/repository/maven-releases/"
  exit 0
fi

if [[ "$CHECK_ONLY" == "true" ]]; then
  log_err "Maven 私服 (Nexus) 端口 $NEXUS_PORT 不可达"
  exit 1
fi

if ! command -v docker >/dev/null 2>&1; then
  log_err "未安装 docker，无法拉起 Nexus —— 请自行准备 $NEXUS_IMAGE 并监听端口 $NEXUS_PORT"
  exit 1
fi

mkdir -p "$NEXUS_DATA"

if docker ps -a --format '{{.Names}}' | grep -qx "$NEXUS_NAME"; then
  log_info "容器 $NEXUS_NAME 已存在但未运行，docker start..."
  docker start "$NEXUS_NAME" >/dev/null || { log_err "$NEXUS_NAME 启动失败"; exit 1; }
else
  log_info "创建 Nexus 容器（$NEXUS_IMAGE，端口 $NEXUS_PORT，数据卷 $NEXUS_DATA）..."
  docker run -d --name "$NEXUS_NAME" \
    -p "$NEXUS_PORT:8081" \
    -v "$NEXUS_DATA:/nexus-data" \
    --restart always \
    "$NEXUS_IMAGE" >/dev/null || { log_err "$NEXUS_NAME 创建失败"; exit 1; }
fi

# Nexus 首次启动要初始化数据库，比一般容器慢，多等一会
log_info "等待 Nexus 就绪（首次启动约 1~2 分钟）..."
for ((i = 0; i < 120; i++)); do
  port_in_use "$NEXUS_PORT" && break
  sleep 1
  (( i % 15 == 0 )) && log_info "  已等待 ${i}s..."
done

if ! port_in_use "$NEXUS_PORT"; then
  log_err "Nexus 启动超时，查看日志：docker logs $NEXUS_NAME"
  exit 1
fi

log_ok "Maven 私服已就绪 → http://127.0.0.1:$NEXUS_PORT"
log_info "发布仓库：http://127.0.0.1:$NEXUS_PORT/repository/maven-releases/"
log_info "初始管理员密码：docker exec $NEXUS_NAME cat /nexus-data/admin.password"
log_info "后续步骤（改密码/发 SDK/业务方接入）见 docs/maven-nexus-deployment.md"
