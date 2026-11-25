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
cd "$PROJECT_ROOT"

project_path() {
  local relative_path="$1"
  if [[ "$relative_path" = /* ]]; then
    printf '%s\n' "$relative_path"
  else
    printf '%s/%s\n' "$PROJECT_ROOT" "$relative_path"
  fi
}

KAFKA_BIN="kafka/bin"
APP_LOG_DIR="$(project_path "$APP_LOG_DIR")"
PRODUCER_LOG_FILE="$(project_path "$PRODUCER_LOG_FILE")"
TOPIC="quickstart-events"
PRODUCER_PID_FILE="$APP_LOG_DIR/producer.pid"
PRODUCER_COMMAND_PID_FILE="$APP_LOG_DIR/producer.command.pid"

if [[ ! -x "$KAFKA_BIN/kafka-console-producer.sh" ]]; then
  printf 'Kafka console producer not found: %s\n' \
    "$KAFKA_BIN/kafka-console-producer.sh" >&2
  exit 1
fi

mkdir -p "$APP_LOG_DIR" "$(dirname "$PRODUCER_LOG_FILE")"
printf 'Starting producer for %s; writing messages and output to %s\n' \
  "$TOPIC" "$PRODUCER_LOG_FILE"
printf 'Enter messages; press Ctrl+D to finish.\n'
printf '%s\n' "$$" >"$PRODUCER_PID_FILE"
cleanup() {
  if [[ -f "$PRODUCER_COMMAND_PID_FILE" ]]; then
    read -r COMMAND_PID <"$PRODUCER_COMMAND_PID_FILE" || true
    if [[ "${COMMAND_PID:-}" =~ ^[0-9]+$ ]] && kill -0 "$COMMAND_PID" 2>/dev/null; then
      kill -TERM "$COMMAND_PID" 2>/dev/null || true
    fi
    rm -f "$PRODUCER_COMMAND_PID_FILE"
  fi
  rm -f "$PRODUCER_PID_FILE"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
tee -a "$PRODUCER_LOG_FILE" | \
  "$KAFKA_BIN/kafka-console-producer.sh" \
    --bootstrap-server localhost:9092 \
    --topic "$TOPIC" \
    2>>"$PRODUCER_LOG_FILE" &
PRODUCER_PIPELINE_PID=$!
printf '%s\n' "$PRODUCER_PIPELINE_PID" >"$PRODUCER_COMMAND_PID_FILE"
wait "$PRODUCER_PIPELINE_PID"
