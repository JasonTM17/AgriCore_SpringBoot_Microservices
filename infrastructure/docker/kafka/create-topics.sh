#!/usr/bin/env bash
set -euo pipefail

bootstrap_server="${KAFKA_BOOTSTRAP_SERVERS:-kafka:19092}"
partitions="${KAFKA_TOPIC_PARTITIONS:-3}"
replication_factor="${KAFKA_TOPIC_REPLICATION_FACTOR:-1}"

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
  /opt/kafka/bin/kafka-topics.sh --bootstrap-server "$bootstrap_server" --create --if-not-exists \
    --topic "$topic" --partitions "$partitions" --replication-factor "$replication_factor"
  for delay in 1000 2000 4000; do
    /opt/kafka/bin/kafka-topics.sh --bootstrap-server "$bootstrap_server" --create --if-not-exists \
      --topic "${topic}-retry-${delay}" --partitions "$partitions" --replication-factor "$replication_factor"
  done
  /opt/kafka/bin/kafka-topics.sh --bootstrap-server "$bootstrap_server" --create --if-not-exists \
    --topic "${topic}.DLT" --partitions "$partitions" --replication-factor "$replication_factor"
done
