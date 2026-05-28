#!/usr/bin/env bash
set -euo pipefail

MODE="physical"
RESTART="true"
LAUNCH_APP="false"

usage() {
  cat <<'USAGE'
Usage:
  scripts/start-demo-stack.sh [physical|emulator] [--launch-app] [--no-restart]

Starts the local Yoshi services needed by the Android demo.

Modes:
  physical   Use adb reverse and ALLOWED_HOST=127.0.0.1. Default.
  emulator   Use emulator host alias and ALLOWED_HOST=10.0.2.2.

Options:
  --launch-app   Build, install, and launch the Android app after services are ready.
  --no-restart   Do not stop existing listeners on ports 8787/8002 before starting.

Stop:
  Press Ctrl-C in this terminal. The script will stop the services it started.
USAGE
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    physical|emulator)
      MODE="$1"
      ;;
    --launch-app)
      LAUNCH_APP="true"
      ;;
    --no-restart)
      RESTART="false"
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown argument: $1" >&2
      usage >&2
      exit 1
      ;;
  esac
  shift
done

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
REPO_ROOT="${YOSHI_REPO_ROOT:-$(cd "$PROJECT_DIR/.." && pwd)}"
LOG_DIR="${TMPDIR:-/tmp}/yoshi-chat-demo"
mkdir -p "$LOG_DIR"

API_PORT="8787"
AGENTS_PORT="8002"

case "$MODE" in
  physical)
    AGENTS_ALLOWED_HOST="127.0.0.1"
    ;;
  emulator)
    AGENTS_ALLOWED_HOST="10.0.2.2"
    ;;
esac

api_pid=""
agents_pid=""

cleanup() {
  set +e
  if [[ -n "$api_pid" ]] && kill -0 "$api_pid" 2>/dev/null; then
    kill "$api_pid" 2>/dev/null
  fi
  if [[ -n "$agents_pid" ]] && kill -0 "$agents_pid" 2>/dev/null; then
    kill "$agents_pid" 2>/dev/null
  fi
}
trap cleanup EXIT INT TERM

require_command() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Missing required command: $1" >&2
    exit 1
  fi
}

resolve_adb() {
  if [[ -n "${ADB:-}" && -x "$ADB" ]]; then
    echo "$ADB"
    return 0
  fi
  if command -v adb >/dev/null 2>&1; then
    command -v adb
    return 0
  fi

  local android_home="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}"
  local sdk_adb="$android_home/platform-tools/adb"
  if [[ -x "$sdk_adb" ]]; then
    echo "$sdk_adb"
    return 0
  fi

  return 1
}

stop_port_listener() {
  local port="$1"
  local pids
  pids="$(lsof -tiTCP:"$port" -sTCP:LISTEN 2>/dev/null || true)"
  if [[ -n "$pids" ]]; then
    echo "Stopping existing listener(s) on port $port: $pids"
    kill $pids 2>/dev/null || true
    sleep 1
  fi
}

wait_for_port() {
  local port="$1"
  local label="$2"
  local attempts=60

  for _ in $(seq 1 "$attempts"); do
    if nc -z 127.0.0.1 "$port" >/dev/null 2>&1; then
      echo "$label is listening on port $port"
      return 0
    fi
    sleep 1
  done

  echo "$label did not start on port $port. Logs:" >&2
  tail -80 "$LOG_DIR"/*.log >&2 || true
  exit 1
}

select_device() {
  local selector="$1"
  local devices
  devices="$("$ADB_BIN" devices | awk 'NR > 1 && $2 == "device" {print $1}')"

  if [[ -n "${ANDROID_SERIAL:-}" ]]; then
    echo "$ANDROID_SERIAL"
    return 0
  fi

  if [[ "$selector" == "physical" ]]; then
    echo "$devices" | grep -v '^emulator-' | head -1
  else
    echo "$devices" | grep '^emulator-' | head -1
  fi
}

require_command pnpm
require_command uv
require_command nc
require_command curl

if [[ ! -d "$REPO_ROOT/apps/api" || ! -d "$REPO_ROOT/apps/agents" ]]; then
  cat >&2 <<EOF
Could not find the Yoshi backend monorepo at:
  $REPO_ROOT

Clone/open the Yoshi monorepo and either:
  1. Place this Android project as a sibling of apps/, or
  2. Set YOSHI_REPO_ROOT=/path/to/yoshi before running this script.
EOF
  exit 1
fi

if [[ "$MODE" == "physical" || "$LAUNCH_APP" == "true" ]]; then
  if ! ADB_BIN="$(resolve_adb)"; then
    echo "Missing adb. Set ANDROID_HOME, ANDROID_SDK_ROOT, or ADB." >&2
    exit 1
  fi
else
  ADB_BIN=""
fi

if [[ "$RESTART" == "true" ]]; then
  stop_port_listener "$API_PORT"
  stop_port_listener "$AGENTS_PORT"
fi

echo "Starting API worker on 0.0.0.0:$API_PORT"
(
  cd "$REPO_ROOT"
  pnpm --filter @yoshi/api exec vite dev --host 0.0.0.0 --port "$API_PORT"
) >"$LOG_DIR/api.log" 2>&1 &
api_pid="$!"

echo "Starting agents on 0.0.0.0:$AGENTS_PORT with ALLOWED_HOST=$AGENTS_ALLOWED_HOST"
(
  cd "$REPO_ROOT/apps/agents"
  ALLOWED_HOST="$AGENTS_ALLOWED_HOST" PYTHONPATH=src uv run uvicorn main:app --host 0.0.0.0 --port "$AGENTS_PORT"
) >"$LOG_DIR/agents.log" 2>&1 &
agents_pid="$!"

wait_for_port "$API_PORT" "API worker"
wait_for_port "$AGENTS_PORT" "Agents service"

host_check_code="$(
  curl -sS -o /dev/null \
    -H "Host: ${AGENTS_ALLOWED_HOST}:${AGENTS_PORT}" \
    -w "%{http_code}" \
    "http://127.0.0.1:${AGENTS_PORT}/api/v1/chat/thread/init" || true
)"
if [[ "$host_check_code" == "400" ]]; then
  echo "Agents rejected Host: ${AGENTS_ALLOWED_HOST}:${AGENTS_PORT}" >&2
  echo "Check $LOG_DIR/agents.log" >&2
  exit 1
fi
echo "Agents host check passed with HTTP $host_check_code"

if [[ "$MODE" == "physical" ]]; then
  device="$(select_device physical)"
  if [[ -z "$device" ]]; then
    echo "No physical Android device found. Connect the device or set ANDROID_SERIAL." >&2
    exit 1
  fi
  echo "Configuring adb reverse for $device"
  "$ADB_BIN" -s "$device" reverse tcp:"$API_PORT" tcp:"$API_PORT"
  "$ADB_BIN" -s "$device" reverse tcp:"$AGENTS_PORT" tcp:"$AGENTS_PORT"
fi

if [[ "$LAUNCH_APP" == "true" ]]; then
  device="$(select_device "$MODE")"
  if [[ -z "$device" ]]; then
    echo "No Android $MODE target found. Set ANDROID_SERIAL or start/connect a device." >&2
    exit 1
  fi
  android_home="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}"
  echo "Building and installing app on $device"
  (
    cd "$PROJECT_DIR"
    ANDROID_HOME="$android_home" ./gradlew :app:assembleDebug --no-daemon
  )
  "$ADB_BIN" -s "$device" install -r "$PROJECT_DIR/app/build/outputs/apk/debug/app-debug.apk"
  "$ADB_BIN" -s "$device" shell am force-stop com.example.yoshichat
  "$ADB_BIN" -s "$device" shell am start -n com.example.yoshichat/.MainActivity
fi

cat <<EOF

Yoshi Android demo stack is ready.

Mode:          $MODE
API:           http://127.0.0.1:$API_PORT
Agents:        http://127.0.0.1:$AGENTS_PORT
Agents host:   $AGENTS_ALLOWED_HOST
Logs:          $LOG_DIR

Keep this terminal open during the demo. Press Ctrl-C to stop.
EOF

wait "$api_pid" "$agents_pid"
