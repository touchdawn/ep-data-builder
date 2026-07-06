#!/usr/bin/env bash
#
# 一键关闭前后端服务
#
set -uo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RUN_DIR="$ROOT_DIR/.run"

stop_one() {
  # $1 = 名称, $2 = pid 文件, $3 = 端口
  local name="$1" pid_file="$2" port="$3"
  local stopped=0

  if [[ -f "$pid_file" ]]; then
    local pid
    pid="$(cat "$pid_file")"
    if kill -0 "$pid" 2>/dev/null; then
      echo "🛑 关闭 $name (PID $pid) ..."
      # 结束整个进程组，确保子进程(gradle/vite)一并退出
      kill -- "-$pid" 2>/dev/null || kill "$pid" 2>/dev/null
      stopped=1
    fi
    rm -f "$pid_file"
  fi

  # 兜底：按端口清理残留进程
  local port_pids
  port_pids="$(lsof -ti tcp:"$port" 2>/dev/null || true)"
  if [[ -n "$port_pids" ]]; then
    echo "🛑 清理占用端口 $port 的进程: $port_pids"
    kill $port_pids 2>/dev/null || true
    stopped=1
  fi

  if [[ "$stopped" -eq 0 ]]; then
    echo "ℹ️  $name 未在运行"
  fi
}

stop_one "前端" "$RUN_DIR/frontend.pid" 7588
stop_one "后端" "$RUN_DIR/backend.pid" 9599

echo ""
echo "✅ 已关闭。"
