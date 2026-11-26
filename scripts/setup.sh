#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib/common.sh
source "$SCRIPT_DIR/lib/common.sh"

require_kafka
mkdir_project_dirs

if [[ ! -f "$SERVER_CONFIG" ]]; then
  printf 'Project KRaft configuration is missing: %s\n' "$SERVER_CONFIG" >&2
  exit 1
fi

mkdir -p "$(dirname "$KAFKA_SERVER_CONFIG")"
cp "$SERVER_CONFIG" "$KAFKA_SERVER_CONFIG"

if [[ -f "$KAFKA_DATA_DIR/meta.properties" ]]; then
  if [[ -z "${KAFKA_CLUSTER_ID:-}" ]] || \
    ! grep -qx "cluster.id=$KAFKA_CLUSTER_ID" "$KAFKA_DATA_DIR/meta.properties"; then
    printf 'Configured cluster ID does not match formatted storage at %s\n' \
      "$KAFKA_DATA_DIR" >&2
    exit 1
  fi
  printf 'KRaft storage is already formatted at %s\n' "$KAFKA_DATA_DIR"
  exit 0
fi

if [[ -n "$(find "$KAFKA_DATA_DIR" -mindepth 1 -maxdepth 1 -print -quit)" ]]; then
  printf 'KRaft data directory is not empty and has no meta.properties: %s\n' \
    "$KAFKA_DATA_DIR" >&2
  exit 1
fi

CLUSTER_ID="$("$KAFKA_BIN/kafka-storage.sh" random-uuid)"
if [[ -z "$CLUSTER_ID" ]]; then
  printf 'Kafka returned an empty cluster ID\n' >&2
  exit 1
fi

if [[ "$(grep -c '^KAFKA_CLUSTER_ID=' "$ENV_FILE")" -ne 1 ]]; then
  printf 'Expected exactly one KAFKA_CLUSTER_ID entry in %s\n' "$ENV_FILE" >&2
  exit 1
fi

ENV_TEMP="$ENV_FILE.tmp.$$"
trap 'rm -f "$ENV_TEMP"' EXIT
awk -v cluster_id="$CLUSTER_ID" '
  /^KAFKA_CLUSTER_ID=/ {
    print "KAFKA_CLUSTER_ID=" cluster_id
    next
  }
  { print }
' "$ENV_FILE" >"$ENV_TEMP"
mv "$ENV_TEMP" "$ENV_FILE"
trap - EXIT

KAFKA_CLUSTER_ID="$CLUSTER_ID"
"$KAFKA_BIN/kafka-storage.sh" format \
  -t "$KAFKA_CLUSTER_ID" \
  -c "$KAFKA_SERVER_CONFIG"

printf 'KRaft storage formatted for cluster %s at %s\n' \
  "$KAFKA_CLUSTER_ID" "$KAFKA_DATA_DIR"
