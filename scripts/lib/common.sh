#!/usr/bin/env bash
set -Eeuo pipefail

PROJECT_ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)"
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
KAFKA_RUNTIME_DIR="$(project_path "$KAFKA_RUNTIME_DIR")"
KAFKA_DATA_DIR="$(project_path "$KAFKA_DATA_DIR")"
KAFKA_BROKER_LOG_DIR="$(project_path "$KAFKA_BROKER_LOG_DIR")"
APP_LOG_DIR="$(project_path "$APP_LOG_DIR")"
PRODUCER_LOG_FILE="$(project_path "$PRODUCER_LOG_FILE")"
CONSUMER_LOG_FILE="$(project_path "$CONSUMER_LOG_FILE")"
CONFIG_DIR="$(project_path "$CONFIG_DIR")"

KAFKA_BIN="kafka/bin"
SERVER_CONFIG="$CONFIG_DIR/kraft/server.properties"
KAFKA_SERVER_CONFIG="kafka/config/kraft/server.properties"
BROKER_PID_FILE="$KAFKA_BROKER_LOG_DIR/kafka.pid"

require_kafka() {
  if [[ ! -x "$KAFKA_BIN/kafka-server-start.sh" ]]; then
    printf 'Kafka distribution is missing or incomplete: %s\n' "$KAFKA_HOME" >&2
    exit 1
  fi
}

mkdir_project_dirs() {
  mkdir -p "$KAFKA_DATA_DIR" "$KAFKA_BROKER_LOG_DIR" "$APP_LOG_DIR"
}

cd "$PROJECT_ROOT"
