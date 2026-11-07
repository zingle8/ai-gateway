#!/usr/bin/env bash
# AI 网关平台 —— 脚本公共库
#
# 提供：颜色输出、日志、端口检测、PID 文件管理、进程优雅停止。
# 由 start.sh / stop.sh / restart.sh / status.sh 通过 source 引入，不单独执行。

set -uo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BACKEND_DIR="$ROOT_DIR/backend"
FRONTEND_DIR="$ROOT_DIR/frontend"
PORTAL_DIR="$FRONTEND_DIR/apps/portal"
LOG_DIR="$ROOT_DIR/logs"
APP_LOG_DIR="$LOG_DIR/app"
mkdir -p "$LOG_DIR" "$APP_LOG_DIR"

# 后端微服务：模块目录名 = finalName，数组顺序即启动顺序
BACKEND_SERVICES=(
  "jv-api-gateway-user|28081"
  "jv-api-gateway-overview|28082"
  "jv-api-gateway-discovery|28083"
  "jv-api-gateway-trace|28084"
  "jv-api-gateway-config|28085"
  "jv-api-gateway-scheduler|28087"
  "jv-api-gateway-proxy|28090"
)

# 前端主应用
FRONTEND_NAME="gateway-portal"
FRONTEND_PORT=28080

# 依赖中间件：名称|主机|端口|required|optional
DEPENDENCIES=(
  "MySQL|127.0.0.1|3306|required"
  "Nacos|127.0.0.1|8080|optional"
  "Redis|127.0.0.1|6379|optional"
)

# 数据库（与 backend/*/src/main/resources/application.yml 保持一致）
DB_HOST="${DB_HOST:-127.0.0.1}"
DB_PORT="${DB_PORT:-3306}"
DB_USER="${DB_USER:-root}"
DB_PASSWORD="${DB_PASSWORD:-Root@123456}"
DB_NAME="${DB_NAME:-api_gateway}"

# 执行 mysql，屏蔽命令行密码告警
mysql_exec() {
  mysql -h"$DB_HOST" -P"$DB_PORT" -u"$DB_USER" -p"$DB_PASSWORD" "$@" 2>&1 \
    | grep -v 'Using a password on the command line'
  return "${PIPESTATUS[0]}"
}

database_exists() {
  mysql_exec -e "USE \`$DB_NAME\`;" >/dev/null 2>&1
}

# ---------------------------------------------------------------- 输出

if [[ -t 1 ]]; then
  C_RED=$'\033[31m'; C_GREEN=$'\033[32m'; C_YELLOW=$'\033[33m'
  C_BLUE=$'\033[34m'; C_CYAN=$'\033[36m'; C_OFF=$'\033[0m'
else
  C_RED=''; C_GREEN=''; C_YELLOW=''; C_BLUE=''; C_CYAN=''; C_OFF=''
fi

log_info() { printf '%s[INFO]%s %s\n' "$C_CYAN"   "$C_OFF" "$*"; }
log_ok()   { printf '%s[ OK ]%s %s\n' "$C_GREEN"  "$C_OFF" "$*"; }
log_warn() { printf '%s[WARN]%s %s\n' "$C_YELLOW" "$C_OFF" "$*"; }
log_err()  { printf '%s[FAIL]%s %s\n' "$C_RED"     "$C_OFF" "$*"; }

title() {
  printf '\n%s================ %s ================%s\n' "$C_BLUE" "$*" "$C_OFF"
}

# ---------------------------------------------------------------- 端口与进程

# 检测端口是否在监听（同时覆盖 IPv4 / IPv6 回环，避免只绑 ::1 时误判为未启动）
port_in_use() {
  local port="$1"
  if command -v nc >/dev/null 2>&1; then
    nc -z -w 1 127.0.0.1 "$port" >/dev/null 2>&1 && return 0
    nc -z -w 1 ::1 "$port" >/dev/null 2>&1 && return 0
    return 1
  elif command -v lsof >/dev/null 2>&1; then
    lsof -nP -iTCP:"$port" -sTCP:LISTEN >/dev/null 2>&1
  else
    (exec 3<>"/dev/tcp/127.0.0.1/$port") >/dev/null 2>&1
  fi
}

wait_for_port() {
  local port="$1" timeout="${2:-90}" elapsed=0
  while (( elapsed < timeout )); do
    if port_in_use "$port"; then return 0; fi
    sleep 1
    elapsed=$((elapsed + 1))
  done
  return 1
}

pid_file() { printf '%s/%s.pid' "$LOG_DIR" "$1"; }

# 平台日志统一切分目录 logs/app/，文件名 = 日期--小时--api-gateway--模块名--日志类型--日志等级.txt
# 应用运行日志由各模块 logback-spring.xml 按小时滚动切分（运行/采集上报 × INFO/WARN/ERROR）；
# 本函数仅用于 nohup 的 CONSOLE 兜底输出（logback 文件损坏或启动早期失败时排查用）
log_file() {
  local ts
  ts="$(date +%Y-%m-%d'--'%H)"
  printf '%s/app/%s--api-gateway--%s--运行--CONSOLE.txt' "$LOG_DIR" "$ts" "$1"
}

read_pid() {
  local f pid
  f="$(pid_file "$1")"
  [[ -f "$f" ]] || return 1
  pid="$(cat "$f" 2>/dev/null || true)"
  [[ -n "$pid" ]] || return 1
  kill -0 "$pid" 2>/dev/null || return 1
  printf '%s' "$pid"
}

# 优雅停止：先 TERM，超时再 KILL
terminate_pid() {
  local pid="$1" grace="${2:-15}" waited=0
  kill -0 "$pid" 2>/dev/null || return 0
  kill -TERM "$pid" 2>/dev/null || true
  while kill -0 "$pid" 2>/dev/null && (( waited < grace )); do
    sleep 1
    waited=$((waited + 1))
  done
  if kill -0 "$pid" 2>/dev/null; then
    kill -KILL "$pid" 2>/dev/null || true
    sleep 1
  fi
}

# 兜底清理端口上的残留进程（仅清理本平台相关进程，避免误杀）
kill_port_owner() {
  local port="$1" pids pid cmd waited
  command -v lsof >/dev/null 2>&1 || return 0
  pids="$(lsof -tiTCP:"$port" -sTCP:LISTEN 2>/dev/null || true)"
  [[ -n "$pids" ]] || return 0
  for pid in $pids; do
    cmd="$(ps -p "$pid" -o command= 2>/dev/null || true)"
    if [[ "$cmd" == *"jv-api-gateway-"*".jar"* || "$cmd" == *vite* ]]; then
      log_warn "端口 $port 存在残留进程 (pid=$pid)，正在清理"
      terminate_pid "$pid" 10
    else
      log_warn "端口 $port 被非本平台进程占用 (pid=$pid)，已跳过"
    fi
  done
}
