#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib/common.sh
source "$SCRIPT_DIR/lib/common.sh"

CLEAN=false
if [[ $# -gt 1 ]] || { [[ $# -eq 1 ]] && [[ "$1" != "--clean" ]]; }; then
  printf 'Usage: %s [--clean]\n' "$0" >&2
  exit 2
fi
if [[ "${1:-}" == "--clean" ]]; then
  CLEAN=true
fi

stop_pid_file() {
  local pid_file="$1"
  local description="$2"
  local pid

  if [[ ! -f "$pid_file" ]]; then
    return
  fi

  if ! read -r pid <"$pid_file" || [[ ! "$pid" =~ ^[1-9][0-9]*$ ]] || [[ "$pid" == "$$" ]]; then
    printf 'Ignoring invalid PID file: %s\n' "$pid_file" >&2
    rm -f "$pid_file"
    return
  fi

  if ! kill -0 "$pid" 2>/dev/null; then
    rm -f "$pid_file"
    return
  fi

  printf 'Stopping %s (PID %s)...\n' "$description" "$pid"
  kill -TERM "$pid" 2>/dev/null || true
  for attempt in {1..10}; do
    if ! kill -0 "$pid" 2>/dev/null; then
      rm -f "$pid_file"
      printf '%s stopped.\n' "$description"
      return
    fi
    sleep 1
  done

  if kill -0 "$pid" 2>/dev/null; then
    printf '%s did not stop after 10 seconds (PID %s); leaving it running.\n' \
      "$description" "$pid" >&2
    return 1
  fi

  rm -f "$pid_file"
  printf '%s stopped.\n' "$description"
}

mkdir -p "$KAFKA_BROKER_LOG_DIR" "$APP_LOG_DIR"
stop_pid_file "$KAFKA_BROKER_LOG_DIR/kafka.pid" "Kafka broker"
stop_pid_file "$APP_LOG_DIR/producer.command.pid" "Kafka producer command"
stop_pid_file "$APP_LOG_DIR/producer.pid" "Kafka producer"
stop_pid_file "$APP_LOG_DIR/consumer.command.pid" "Kafka consumer command"
stop_pid_file "$APP_LOG_DIR/consumer.pid" "Kafka consumer"

if [[ "$CLEAN" == true ]]; then
  printf 'Removing contents of %s and %s...\n' "$KAFKA_RUNTIME_DIR" "$APP_LOG_DIR"
  PROJECT_ROOT_REAL="$(cd -- "$PROJECT_ROOT" && pwd -P)"
  ensure_project_directory() {
    local directory="$1"
    local resolved_directory
    mkdir -p "$directory"
    resolved_directory="$(cd -- "$directory" && pwd -P)"
    case "$resolved_directory/" in
      "$PROJECT_ROOT_REAL"/*) ;;
      *)
        printf 'Refusing to clean path outside the project: %s\n' "$resolved_directory" >&2
        exit 1
        ;;
    esac
    if [[ "$resolved_directory" == "$PROJECT_ROOT_REAL" ]]; then
      printf 'Refusing to clean the project root.\n' >&2
      exit 1
    fi
  }
  ensure_project_directory "$KAFKA_RUNTIME_DIR"
  ensure_project_directory "$APP_LOG_DIR"
  find "$KAFKA_RUNTIME_DIR" -mindepth 1 -maxdepth 1 -exec rm -rf -- {} +
  find "$APP_LOG_DIR" -mindepth 1 -maxdepth 1 -exec rm -rf -- {} +
  printf 'Runtime and application log contents removed.\n'
else
  printf 'Kafka tools stopped. Runtime data and logs were kept.\n'
fi
