# Architecture decision records

Accepted decisions are append-only. Supersede an ADR with a new record rather
than rewriting history.

## Required platform decision map

| Decision subject | ADR |
|---|---|
| Monorepo strategy | [0011](0011-monorepo-strategy.md) |
| Database per service | [0012](0012-database-per-service.md) |
| Kafka event communication | [0013](0013-kafka-event-communication.md) |
| Transactional outbox | [0004](0004-transactional-outbox-polling.md) |
| Idempotent consumer | [0005](0005-idempotent-consumer.md) |
| Authentication strategy | [0003](0003-jwt-rs256-jwks.md), [0007](0007-domain-service-jwt-jwks.md) |
| API gateway | [0014](0014-api-gateway-and-same-origin-edge.md) |
| Observability stack | [0010](0010-observability-stack.md) |
| IoT communication with MQTT | [0015](0015-authenticated-mqtt-iot-ingestion.md) |
| Saga orchestration | [0006](0006-sales-saga-orchestration.md), [0008](0008-authoritative-inventory-reservation-reconciliation.md) |

Other records capture decisions discovered during delivery, including the lack
of a service registry and the persisted assistant boundary.

## Lifecycle

1. Copy [the template](template.md).
2. Use the next four-digit number and a descriptive kebab-case name.
3. Include context, decision, consequences, alternatives, trade-offs, and
   evidence links.
4. Mark the record `Accepted` only after implementation and verification agree.
