#!/usr/bin/env bash
set -Eeuo pipefail

PROJECT_ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="$PROJECT_ROOT/config/kafka.env"

if [[ ! -f "$ENV_FILE" ]]; then
  printf 'Kafka environment file not found: %s\n' "$ENV_FILE" >&2
  exit 1
fi

set -a
# shellcheck source=/dev/null
source "$ENV_FILE"
set +a

project_path() {
  local relative_path="$1"
  if [[ "$relative_path" = /* ]]; then
    printf '%s\n' "$relative_path"
  else
    printf '%s/%s\n' "$PROJECT_ROOT" "$relative_path"
  fi
}

KAFKA_HOME="$(project_path "$KAFKA_HOME")"
KAFKA_DATA_DIR="$(project_path "$KAFKA_DATA_DIR")"
KAFKA_BROKER_LOG_DIR="$(project_path "$KAFKA_BROKER_LOG_DIR")"
KAFKA_BIN="kafka/bin"
KAFKA_SERVER_START="$KAFKA_BIN/kafka-server-start.sh"
KAFKA_SERVER_CONFIG="$KAFKA_BIN/../config/kraft/server.properties"
BROKER_LOG="$KAFKA_BROKER_LOG_DIR/kafka.log"
BROKER_PID_FILE="$KAFKA_BROKER_LOG_DIR/kafka.pid"

MODE="${1:-background}"
if [[ $# -gt 1 ]] || [[ "$MODE" != "foreground" && "$MODE" != "background" ]]; then
  printf 'Usage: %s [foreground|background]\n' "$0" >&2
  exit 2
fi

cd "$PROJECT_ROOT"
LOG_DIR="kafka-runtime/brokER-logs"
KAFKA_LOG4J_OPTS="-Dlog4j2.configurationFile=kafka/config/log4j2.yaml"
export LOG_DIR KAFKA_LOG4J_OPTS

if [[ ! -x "$KAFKA_SERVER_START" ]]; then
  printf 'Kafka server launcher not found: %s\n' "$KAFKA_SERVER_START" >&2
  exit 1
fi

if [[ -z "${KAFKA_CLUSTER_ID:-}" ]]; then
  printf 'KAFKA_CLUSTER_ID is empty in %s. Run scripts/setup.sh first.\n' \
    "$ENV_FILE" >&2
  exit 1
fi

if [[ ! -f "$KAFKA_DATA_DIR/meta.properties" ]]; then
  printf 'KRaft storage is not formatted at %s. Run scripts/setup.sh first.\n' \
    "$KAFKA_DATA_DIR" >&2
  exit 1
fi

mkdir -p "$KAFKA_BROKER_LOG_DIR"

if "$KAFKA_BIN/kafka-broker-api-versions.sh" \
  --bootstrap-server "$KAFKA_BOOTSTRAP_SERVER" >/dev/null 2>&1; then
  printf 'Kafka is already running at %s\n' "$KAFKA_BOOTSTRAP_SERVER"
  exit 0
fi

if [[ -f "$BROKER_PID_FILE" ]]; then
  read -r EXISTING_PID <"$BROKER_PID_FILE" || true
  if [[ -n "${EXISTING_PID:-}" ]] && kill -0 "$EXISTING_PID" 2>/dev/null; then
    printf 'Kafka launcher is already running with PID %s; check %s\n' \
      "$EXISTING_PID" "$BROKER_LOG" >&2
    exit 1
  fi
  rm -f "$BROKER_PID_FILE"
fi

if [[ "$MODE" == "foreground" ]]; then
  printf 'Starting Kafka in foreground; broker log: %s\n' "$BROKER_LOG"
  "$KAFKA_SERVER_START" "$KAFKA_SERVER_CONFIG" >>"$BROKER_LOG" 2>&1 &
  KAFKA_PID=$!
  printf '%s\n' "$KAFKA_PID" >"$BROKER_PID_FILE"

  if wait "$KAFKA_PID"; then
    EXIT_STATUS=0
  else
    EXIT_STATUS=$?
  fi

  rm -f "$BROKER_PID_FILE"
  printf 'Kafka broker stopped (exit code %s).\n' "$EXIT_STATUS"
  exit "$EXIT_STATUS"
fi

nohup bash -c '
  server_start="$1"
  server_config="$2"
  broker_log="$3"
  pid_file="$4"

  broker_stopped() {
    exit_status=$?
    trap - EXIT INT TERM
    if [[ -n "${broker_pid:-}" ]] && kill -0 "$broker_pid" 2>/dev/null; then
      kill -TERM "$broker_pid" 2>/dev/null || true
      wait "$broker_pid" 2>/dev/null || true
    fi
    printf "Kafka broker stopped (exit code %s).\n" "$exit_status" >>"$broker_log"
    rm -f "$pid_file"
    exit "$exit_status"
  }

  trap "exit 130" INT
  trap "exit 143" TERM
  trap broker_stopped EXIT

  printf "Kafka broker starting (PID %s).\n" "$$" >>"$broker_log"
  "$server_start" "$server_config" >>"$broker_log" 2>&1 &
  broker_pid=$!
  printf "%s\n" "$$" >"$pid_file"
  wait "$broker_pid"
' kafka-background "$KAFKA_SERVER_START" "$KAFKA_SERVER_CONFIG" \
  "$BROKER_LOG" "$BROKER_PID_FILE" </dev/null >/dev/null 2>&1 &
KAFKA_PID=$!
printf 'Starting Kafka in background (PID %s); broker log: %s\n' \
  "$KAFKA_PID" "$BROKER_LOG"
