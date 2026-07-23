#!/usr/bin/env bash
set -euo pipefail

bootstrap_server="${KAFKA_BOOTSTRAP_SERVERS:-kafka:19092}"
partitions="${KAFKA_TOPIC_PARTITIONS:-3}"
replication_factor="${KAFKA_TOPIC_REPLICATION_FACTOR:-1}"
retry_retention_ms="${KAFKA_RETRY_TOPIC_RETENTION_MS:-86400000}"
dlt_retention_ms="${KAFKA_DLT_TOPIC_RETENTION_MS:-604800000}"

validate_positive_milliseconds() {
  local name="$1"
  local value="$2"
  if [[ ! "$value" =~ ^[1-9][0-9]*$ ]]; then
    echo "$name must be a positive retention in milliseconds, got: $value" >&2
    exit 64
  fi
}

create_topic() {
  local topic="$1"
  shift
  /opt/kafka/bin/kafka-topics.sh --bootstrap-server "$bootstrap_server" --create --if-not-exists \
    --topic "$topic" --partitions "$partitions" --replication-factor "$replication_factor" "$@"
}

ensure_bounded_retention() {
  local topic="$1"
  local retention_ms="$2"
  create_topic "$topic" --config "cleanup.policy=delete" --config "retention.ms=${retention_ms}"
  /opt/kafka/bin/kafka-configs.sh --bootstrap-server "$bootstrap_server" --alter \
    --entity-type topics --entity-name "$topic" \
    --add-config "cleanup.policy=delete,retention.ms=${retention_ms}"
}

validate_positive_milliseconds KAFKA_RETRY_TOPIC_RETENTION_MS "$retry_retention_ms"
validate_positive_milliseconds KAFKA_DLT_TOPIC_RETENTION_MS "$dlt_retention_ms"

topics=(
  agricore.farm.events
  agricore.crop-cycle.events
  agricore.work.events
  agricore.harvest.events
  agricore.inventory.events
  agricore.iot.events
  agricore.traceability.events
  agricore.sales.events
  agricore.notification.events
)

for topic in "${topics[@]}"; do
  create_topic "$topic"
  for delay in 1000 2000 4000; do
    ensure_bounded_retention "${topic}-retry-${delay}" "$retry_retention_ms"
  done
  ensure_bounded_retention "${topic}.DLT" "$dlt_retention_ms"
done
