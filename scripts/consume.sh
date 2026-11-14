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
CONSUMER_LOG_FILE="$(project_path "$CONSUMER_LOG_FILE")"
TOPIC="quickstart-events"
CONSUMER_PID_FILE="$APP_LOG_DIR/consumer.pid"
CONSUMER_COMMAND_PID_FILE="$APP_LOG_DIR/consumer.command.pid"

if [[ ! -x "$KAFKA_BIN/kafka-console-consumer.sh" ]]; then
  printf 'Kafka console consumer not found: %s\n' \
    "$KAFKA_BIN/kafka-console-consumer.sh" >&2
  exit 1
fi

mkdir -p "$APP_LOG_DIR" "$(dirname "$CONSUMER_LOG_FILE")"
printf 'Starting consumer for %s; writing output to %s\n' \
  "$TOPIC" "$CONSUMER_LOG_FILE"
printf '%s\n' "$$" >"$CONSUMER_PID_FILE"
cleanup() {
  if [[ -f "$CONSUMER_COMMAND_PID_FILE" ]]; then
    read -r COMMAND_PID <"$CONSUMER_COMMAND_PID_FILE" || true
    if [[ "${COMMAND_PID:-}" =~ ^[0-9]+$ ]] && kill -0 "$COMMAND_PID" 2>/dev/null; then
      kill -TERM "$COMMAND_PID" 2>/dev/null || true
    fi
    rm -f "$CONSUMER_COMMAND_PID_FILE"
  fi
  rm -f "$CONSUMER_PID_FILE"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
"$KAFKA_BIN/kafka-console-consumer.sh" \
  --bootstrap-server localhost:9092 \
  --topic "$TOPIC" \
  --from-beginning >"$CONSUMER_LOG_FILE" 2>&1 &
CONSUMER_COMMAND_PID=$!
printf '%s\n' "$CONSUMER_COMMAND_PID" >"$CONSUMER_COMMAND_PID_FILE"
wait "$CONSUMER_COMMAND_PID"
