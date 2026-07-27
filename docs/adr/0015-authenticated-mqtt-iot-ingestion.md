# 15. Authenticated MQTT for IoT ingestion

**Date:** 2026-07-23

**Status:** Accepted

## Context

Field devices need a lightweight protocol that tolerates intermittent networks
and duplicate delivery. Device telemetry must remain isolated by topic, map to a
registered plot, and drive alert events without creating repeated work or
notifications.

## Decision

Use MQTT QoS 1 for device ingress and a bounded IoT ingestion worker.

- Devices publish JSON to `agricore/telemetry/{deviceCode}/reading`.
- Broker authentication and ACLs restrict a device user to its own topic.
- Payload `deviceCode` must match the topic and a registered device.
- Before shared-queue submission, each normalized device code has an independent
  token bucket and in-flight cap. The number of tracked buckets and their idle
  lifetime are bounded. Invalid topics share one bounded bucket instead of
  allocating attacker-controlled state.
- A stable `readingId` is the global idempotency key. Exact redelivery is ignored;
  reuse with different canonical telemetry is rejected.
- IoT persists readings in its own time-series-capable PostgreSQL database,
  evaluates versioned rules, and stores alert fingerprints, cooldown, status, and
  device last-seen state.
- Alert and offline events leave IoT through the transactional outbox and Kafka.
- HTTP ingestion uses the same application service after JWT and plot-access
  checks; it is an operations path, not an anonymous device substitute.

## Consequences

### Positive

- QoS 1 avoids silent best-effort loss while application idempotency makes
  redelivery safe.
- Per-device ACLs and topic/payload matching reduce spoofing scope.
- One noisy device cannot consume another device's admission capacity.
- Cooldowns suppress notification storms from repeated abnormal readings.

### Negative

- Broker identity lifecycle, TLS, and ACL distribution are production operator
  responsibilities.
- QoS 1 does not guarantee exactly-once processing.
- Telemetry growth needs explicit capacity, retention, and backup policy.
- A rate-limited record is acknowledged and counted rather than retried; global
  worker-queue saturation disconnects so broker redelivery can apply backpressure.

### Trade-offs

The local profile favors reproducible username/password ACLs and cleartext
loopback access. Production must add TLS and managed credentials. No destructive
retention is selected without a product-owned horizon and storage budget.

## Alternatives considered

- **HTTP-only ingestion:** rejected because connection overhead and retry behavior
  are a poor fit for many intermittent devices.
- **MQTT QoS 0:** rejected because transient loss would be invisible.
- **MQTT QoS 2:** deferred because protocol overhead is higher and application
  idempotency is still required across broker/application failures.
- **Publish devices directly to Kafka:** rejected because Kafka credentials,
  protocol weight, and network exposure are unsuitable for field devices.

## References

- [MQTT AsyncAPI contract](../../contracts/asyncapi/agricore-mqtt.yaml)
- [MQTT simulator Compose profile](../../docker-compose.mqtt-simulator.yml)
- [IoT ingestion diagram](../diagrams/iot-ingestion-flow.md)
- [Local operations runbook](../runbooks/local-operations.md)
