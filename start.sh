#!/usr/bin/env bash
#
# 一键启动前后端服务
#   后端: Spring Boot (Gradle bootRun)  端口 9599
#   前端: Vite (npm run dev)            端口 7588
#
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SERVER_DIR="$ROOT_DIR/ep-data-builder-server"
WEB_DIR="$ROOT_DIR/ep-data-builder-web"
RUN_DIR="$ROOT_DIR/.run"
LOG_DIR="$RUN_DIR/logs"

mkdir -p "$LOG_DIR"

is_running() {
  # $1 = pid file
  [[ -f "$1" ]] && kill -0 "$(cat "$1")" 2>/dev/null
}

start_backend() {
  local pid_file="$RUN_DIR/backend.pid"
  if is_running "$pid_file"; then
    echo "⚠️  后端已在运行 (PID $(cat "$pid_file"))，跳过"
    return
  fi
  echo "🚀 启动后端 (端口 9599) ..."
  cd "$SERVER_DIR"
  # 本项目为 Spring Boot 2.1 + Gradle 5.6.4，需用 Java 8 运行（JDK 17 会导致 Gradle 崩溃）
  local java8
  java8="$(/usr/libexec/java_home -v 1.8 2>/dev/null || true)"
  if [[ -z "$java8" ]]; then
    echo "❌ 未找到 Java 8，请先安装 (brew install --cask corretto8)。后端启动中止。"
    return
  fi
  JAVA_HOME="$java8" nohup ./gradlew bootRun --console=plain > "$LOG_DIR/backend.log" 2>&1 &
  echo $! > "$pid_file"
  echo "   PID $(cat "$pid_file")  日志: $LOG_DIR/backend.log"
}

start_frontend() {
  local pid_file="$RUN_DIR/frontend.pid"
  if is_running "$pid_file"; then
    echo "⚠️  前端已在运行 (PID $(cat "$pid_file"))，跳过"
    return
  fi
  echo "🚀 启动前端 (端口 7588) ..."
  cd "$WEB_DIR"
  if [[ ! -d node_modules ]]; then
    echo "   未发现 node_modules，先执行 npm install ..."
    npm install
  fi
  nohup npm run dev > "$LOG_DIR/frontend.log" 2>&1 &
  echo $! > "$pid_file"
  echo "   PID $(cat "$pid_file")  日志: $LOG_DIR/frontend.log"
}

start_backend
start_frontend

echo ""
echo "✅ 已启动。"
echo "   前端: http://127.0.0.1:7588"
echo "   后端: http://127.0.0.1:9599"
echo "   查看日志: tail -f $LOG_DIR/backend.log  或  $LOG_DIR/frontend.log"
echo "   关闭服务: ./stop.sh"
