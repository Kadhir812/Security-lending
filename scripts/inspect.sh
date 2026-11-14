#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib/common.sh
source "$SCRIPT_DIR/lib/common.sh"
require_kafka

MODE="${1:-}"
if [[ $# -ne 1 ]] || [[ "$MODE" != "list" && "$MODE" != "describe" && "$MODE" != "tail" ]]; then
  printf 'Usage: %s {list|describe|tail}\n' "$0" >&2
  exit 2
fi

case "$MODE" in
  list)
    "$KAFKA_BIN/kafka-topics.sh" --list --bootstrap-server localhost:9092
    ;;
  describe)
    printf 'Topic details for quickstart-events:\n'
    "$KAFKA_BIN/kafka-topics.sh" \
      --describe \
      --topic quickstart-events \
      --bootstrap-server localhost:9092
    printf '\nHighWatermark / latest log offsets (single replica):\n'
    "$KAFKA_BIN/kafka-get-offsets.sh" \
      --topic quickstart-events \
      --time -1 \
      --bootstrap-server localhost:9092
    ;;
  tail)
    mkdir -p "$KAFKA_BROKER_LOG_DIR" "$APP_LOG_DIR"
    touch "$PRODUCER_LOG_FILE" "$CONSUMER_LOG_FILE"
    shopt -s nullglob
    BROKER_LOG_FILES=("$KAFKA_BROKER_LOG_DIR"/*.log)
    shopt -u nullglob
    if ((${#BROKER_LOG_FILES[@]} == 0)); then
      BROKER_LOG_FILES=("$KAFKA_BROKER_LOG_DIR/kafka.log")
    fi
    printf 'Following broker and application logs; press Ctrl+C to stop.\n'
    tail -f "${BROKER_LOG_FILES[@]}" "$PRODUCER_LOG_FILE" "$CONSUMER_LOG_FILE"
    ;;
esac
