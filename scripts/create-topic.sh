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
TOPIC_LOG_FILE="$APP_LOG_DIR/create-topic.log"
TOPIC="quickstart-events"

if [[ ! -x "$KAFKA_BIN/kafka-topics.sh" ]]; then
  printf 'Kafka topic tool not found: %s\n' "$KAFKA_BIN/kafka-topics.sh" >&2
  exit 1
fi

mkdir -p "$APP_LOG_DIR"
printf 'Creating topic %s; writing output to %s\n' "$TOPIC" "$TOPIC_LOG_FILE"
"$KAFKA_BIN/kafka-topics.sh" \
  --bootstrap-server localhost:9092 \
  --create --if-not-exists \
  --topic "$TOPIC" \
  --partitions 1 \
  --replication-factor 1 2>&1 | tee -a "$TOPIC_LOG_FILE"
