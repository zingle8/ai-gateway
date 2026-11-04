#!/usr/bin/env bash
# AI 网关平台 —— 本机依赖容器准备（MySQL / Nacos / Redis）
#
# 幂等：容器已存在直接 docker start；不存在则按下方默认参数创建。
# 创建参数与项目演示环境一致（端口 / 密码 / 镜像），可按需修改；
# 端口已被非容器进程占用时（如本机 brew 安装的 MySQL）视为可用，跳过 docker 操作。
#
# 用法:
#   ./init-env.sh               # 检查并拉起全部（MySQL / Nacos / Redis）
#   ./init-env.sh mysql redis   # 只处理指定项（可选值：mysql / nacos / redis）
#   ./init-env.sh --check       # 只检查不拉起，全部可达返回 0（供 start.sh 复查）
#
# Nacos 说明:
#   控制台 8080，服务注册/配置走 8848，v2.x 的 gRPC 还需要 9848（8848+1000）。
#   项目以 optional: 方式引入 Nacos，它挂了不影响后端启动，只失去动态配置能力。

set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=_common.sh
source "$SCRIPT_DIR/_common.sh"

REDIS_PASSWORD="${REDIS_PASSWORD:-Redis@123456}"

# ---------------------------------------------------------------- 解析参数

TARGETS=()
CHECK_ONLY=false
for arg in "$@"; do
  case "$arg" in
    --check) CHECK_ONLY=true ;;
    mysql|nacos|redis) TARGETS+=("$arg") ;;
    *) log_err "未知参数：$arg"; echo "用法: $0 [--check] [mysql|nacos|redis ...]"; exit 1 ;;
  esac
done
[[ ${#TARGETS[@]} -eq 0 ]] && TARGETS=(mysql nacos redis)

# ---------------------------------------------------------------- 容器定义
# 名称|探测端口|镜像|创建命令

NACOS_IMAGE="nacos/nacos-server:latest"
MYSQL_IMAGE="mysql:5.7"
REDIS_IMAGE="redis:7.2-alpine"

CONTAINERS=(
  "mysql|$DB_PORT|$MYSQL_IMAGE|docker run -d --name mysql57 -p ${DB_PORT}:3306 -e MYSQL_ROOT_PASSWORD='$DB_PASSWORD' --restart always $MYSQL_IMAGE"
  "nacos|8080|$NACOS_IMAGE|docker run -d --name nacos -p 8080:8080 -p 8848:8848 -p 9848:9848 -e MODE=standalone --restart always $NACOS_IMAGE"
  "redis|6379|$REDIS_IMAGE|docker run -d --name redis -p 6379:6379 $REDIS_IMAGE redis-server --requirepass '$REDIS_PASSWORD' --restart always"
)

# 容器名映射（与 docker ps 里的一致）
container_name() {
  case "$1" in
    mysql) echo "mysql57" ;;
    nacos) echo "nacos" ;;
    redis) echo "redis" ;;
  esac
}

# ---------------------------------------------------------------- 拉起逻辑

wait_port() {
  local port="$1" tries="${2:-30}"
  for ((i = 0; i < tries; i++)); do
    port_in_use "$port" && return 0
    sleep 1
  done
  return 1
}

ensure_one() {
  local service="$1" port image cmd cname
  for def in "${CONTAINERS[@]}"; do
    IFS='|' read -r svc port image cmd <<<"$def"
    [[ "$svc" == "$service" ]] && break
  done
  cname="$(container_name "$service")"

  # 1. 端口已可达：无论是不是容器提供的，都视为就绪
  if port_in_use "$port"; then
    log_ok "$service ($image) 端口 $port 可达"
    return 0
  fi

  if [[ "$CHECK_ONLY" == "true" ]]; then
    log_err "$service ($cname) 端口 $port 不可达"
    return 1
  fi

  if ! command -v docker >/dev/null 2>&1; then
    log_err "未安装 docker，无法拉起 $service —— 请自行准备 $image 并监听端口 $port"
    return 1
  fi

  # 2. 容器已创建（可能处于停止状态）：直接启动
  if docker ps -a --format '{{.Names}}' | grep -qx "$cname"; then
    log_info "$service 容器 $cname 已存在但未运行，docker start..."
    docker start "$cname" >/dev/null || { log_err "$cname 启动失败"; return 1; }
  else
    # 3. 容器不存在：按默认参数创建
    log_info "$service 容器 $cname 不存在，创建（镜像 $image）..."
    # shellcheck disable=SC2086
    eval "$cmd" >/dev/null || { log_err "$cname 创建失败"; return 1; }
  fi

  if wait_port "$port" 30; then
    log_ok "$service ($cname) 已拉起 → 端口 $port"
    return 0
  fi
  log_err "$service 启动超时（30s），查看日志：docker logs $cname"
  return 1
}

# ---------------------------------------------------------------- main

title "准备依赖中间件${CHECK_ONLY:+（仅检查）}"
failed=0
for t in "${TARGETS[@]}"; do
  ensure_one "$t" || failed=$((failed + 1))
done

if (( failed > 0 )); then
  log_err "$failed 个依赖未就绪"
  exit 1
fi
[[ "$CHECK_ONLY" == "true" ]] && log_ok "全部依赖就绪"
exit 0
