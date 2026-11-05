#!/usr/bin/env bash
# AI 网关平台 —— 一键启动
#
# 用法:
#   ./start.sh                 # 启动全部（默认先 mvn clean package 全量清理打包后端）
#   ./start.sh backend         # 只启动后端
#   ./start.sh frontend        # 只启动前端
#   ./start.sh all --skip-build# 跳过构建直接启动（jar 已存在时）
#
# 说明:
#   - 必需中间件（MySQL）不可达时，自动尝试 init-env.sh 拉起 docker 容器
#   - 后端启动前默认执行全子模块依赖清理与打包（mvn clean package -DskipTests），
#     加 --skip-build 可跳过；jar 不存在时无论如何都会构建
#   - 端口被占用且无本脚本 PID 记录时跳过该服务，不会强杀其他进程
#   - JVM 参数可通过环境变量覆盖：JAVA_OPTS="-Xms512m -Xmx1g" ./start.sh
#
# 日志目录: logs/（平台日志按 日期--时间--平台名--模块名--日志类型--日志等级.txt 切分于 logs/app/）

set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=_common.sh
source "$SCRIPT_DIR/_common.sh"

TARGET="${1:-all}"
SKIP_BUILD=false
for arg in "$@"; do
  [[ "$arg" == "--skip-build" ]] && SKIP_BUILD=true
done

# ---------------------------------------------------------------- 环境检查

check_command() {
  if ! command -v "$1" >/dev/null 2>&1; then
    log_err "未找到命令：$1"
    return 1
  fi
  return 0
}

check_dependencies() {
  local missing_required=false
  title "检查依赖中间件"
  for dep in "${DEPENDENCIES[@]}"; do
    IFS='|' read -r name host port level <<<"$dep"
    if port_in_use "$port"; then
      log_ok "$name ($host:$port) 可达"
    elif [[ "$level" == "required" ]]; then
      # MySQL 没起：先尝试自动拉起 docker 容器，拉不起来才判失败
      log_warn "$name ($host:$port) 不可达，尝试自动拉起容器..."
      if "$SCRIPT_DIR/init-env.sh" "$(echo "${name,,}" | tr 'A-Z' 'a-z')" >/dev/null 2>&1 \
        && port_in_use "$port"; then
        log_ok "$name ($host:$port) 已自动拉起"
      else
        log_err "$name ($host:$port) 不可达 —— 后端服务将无法启动"
        printf '%s  可手动准备：%s/init-env.sh%s\n' "$C_YELLOW" "$SCRIPT_DIR" "$C_OFF"
        missing_required=true
      fi
    else
      log_warn "$name ($host:$port) 不可达 —— 相关功能降级，不影响启动"
    fi
  done

  if command -v mysql >/dev/null 2>&1; then
    if database_exists; then
      log_ok "数据库 $DB_NAME 已就绪"
    else
      log_err "数据库 $DB_NAME 不存在 —— 后端服务将无法启动"
      printf '%s  初始化：%s/init-db.sh%s\n' "$C_YELLOW" "$SCRIPT_DIR" "$C_OFF"
      missing_required=true
    fi
  else
    log_warn "未找到 mysql 客户端，跳过数据库校验"
  fi

  [[ "$missing_required" == "true" ]] && return 1
  return 0
}

# ---------------------------------------------------------------- 后端

start_backend() {
  title "启动后端微服务"

  check_command java || return 1
  check_dependencies || return 1

  # 全子模块依赖清理与打包（mvn clean package 会重编全部模块，保证产物与源码一致）
  if [[ "$SKIP_BUILD" == "true" ]]; then
    log_warn "已指定 --skip-build，跳过全量清理打包"
  else
    log_info "全子模块依赖清理与打包（mvn clean package -DskipTests）..."
    (cd "$BACKEND_DIR" && mvn -B clean package -DskipTests) >"$LOG_DIR/build-backend.log" 2>&1 || {
      log_err "后端构建失败，详见 $LOG_DIR/build-backend.log"
      tail -n 40 "$LOG_DIR/build-backend.log"
      return 1
    }
    log_ok "后端构建完成"
  fi

  local started=0 skipped=0 failed=0
  for svc in "${BACKEND_SERVICES[@]}"; do
    IFS='|' read -r name port <<<"$svc"
    local jar="$BACKEND_DIR/$name/target/$name.jar"

    if [[ ! -f "$jar" ]]; then
      log_info "$name 未找到 jar，开始构建..."
      (cd "$BACKEND_DIR" && mvn -B -DskipTests package) >"$LOG_DIR/build-backend.log" 2>&1 || {
        log_err "$name 构建失败，详见 $LOG_DIR/build-backend.log"
        tail -n 40 "$LOG_DIR/build-backend.log"
        failed=$((failed + 1))
        continue
      }
    fi

    if [[ -n "$(read_pid "$name" || true)" ]]; then
      log_warn "$name 已在运行 (pid=$(read_pid "$name"))，跳过"
      skipped=$((skipped + 1))
      continue
    fi

    if port_in_use "$port"; then
      log_warn "端口 $port 已被占用且无本脚本 PID 记录，跳过 $name"
      log_warn "  排查：lsof -i :$port   清理：./stop.sh 或 kill -9 <pid>"
      skipped=$((skipped + 1))
      continue
    fi

    log_info "启动 $name (端口 $port)..."
    # shellcheck disable=SC2086
    nohup java ${JAVA_OPTS:--Xms256m -Xmx512m} -jar "$jar" >"$(log_file "$name")" 2>&1 &
    echo $! >"$(pid_file "$name")"

    if wait_for_port "$port" 90; then
      log_ok "$name 启动成功 → http://127.0.0.1:$port/api"
      started=$((started + 1))
    else
      log_err "$name 启动超时（90s），日志：$(log_file "$name")"
      tail -n 20 "$(log_file "$name")" 2>/dev/null || true
      rm -f "$(pid_file "$name")"
      failed=$((failed + 1))
    fi
  done

  printf '\n后端汇总：启动 %s 个，跳过 %s 个，失败 %s 个\n' "$started" "$skipped" "$failed"
  (( failed > 0 )) && return 1
  return 0
}

# ---------------------------------------------------------------- 前端

start_frontend() {
  title "启动前端主应用"

  check_command node || return 1
  check_command npm || return 1

  if [[ ! -d "$FRONTEND_DIR/node_modules" ]]; then
    log_info "首次启动，安装前端依赖..."
    (cd "$FRONTEND_DIR" && npm install) >"$LOG_DIR/build-frontend.log" 2>&1 || {
      log_err "前端依赖安装失败，详见 $LOG_DIR/build-frontend.log"
      tail -n 30 "$LOG_DIR/build-frontend.log"
      return 1
    }
    log_ok "前端依赖安装完成"
  fi

  if [[ -n "$(read_pid "$FRONTEND_NAME" || true)" ]]; then
    log_warn "$FRONTEND_NAME 已在运行 (pid=$(read_pid "$FRONTEND_NAME"))，跳过"
    return 0
  fi

  if port_in_use "$FRONTEND_PORT"; then
    log_warn "端口 $FRONTEND_PORT 已被占用且无本脚本 PID 记录，跳过 $FRONTEND_NAME"
    return 0
  fi

  local vite_bin="$FRONTEND_DIR/node_modules/.bin/vite"
  [[ -x "$vite_bin" ]] || { log_err "未找到 vite：$vite_bin"; return 1; }

  log_info "启动 $FRONTEND_NAME (端口 $FRONTEND_PORT)..."
  # 显式绑定 127.0.0.1：vite 默认绑 localhost，在部分环境只监听 IPv6 回环，
  # 导致按 127.0.0.1 探测端口时误判为启动失败。
  pushd "$PORTAL_DIR" >/dev/null || return 1
  nohup "$vite_bin" --host 127.0.0.1 --port "$FRONTEND_PORT" >"$(log_file "$FRONTEND_NAME")" 2>&1 &
  echo $! >"$(pid_file "$FRONTEND_NAME")"
  popd >/dev/null || true

  if wait_for_port "$FRONTEND_PORT" 60; then
    log_ok "$FRONTEND_NAME 启动成功 → http://127.0.0.1:$FRONTEND_PORT"
    return 0
  fi
  log_err "$FRONTEND_NAME 启动超时，日志：$(log_file "$FRONTEND_NAME")"
  tail -n 20 "$(log_file "$FRONTEND_NAME")" 2>/dev/null || true
  rm -f "$(pid_file "$FRONTEND_NAME")"
  return 1
}

# ---------------------------------------------------------------- main

case "$TARGET" in
  all)      start_backend && start_frontend ;;
  backend)  start_backend ;;
  frontend) start_frontend ;;
  -h|--help) sed -n '2,15p' "${BASH_SOURCE[0]}"; exit 0 ;;
  *)        log_err "未知参数：$TARGET"; echo "用法: $0 [all|backend|frontend] [--build]"; exit 1 ;;
esac

rc=$?

title "访问地址"
printf '  控制台前端   http://127.0.0.1:%s\n' "$FRONTEND_PORT"
for svc in "${BACKEND_SERVICES[@]}"; do
  IFS='|' read -r name port <<<"$svc"
  printf '  %-13s http://127.0.0.1:%s/api\n' "$name" "$port"
done
printf '\n  日志目录: %s\n' "$LOG_DIR"

exit $rc
