# Architecture decision records

Accepted decisions are append-only. Supersede an ADR with a new record rather
than rewriting history.

## Decision index

| ADR | Decision | Status | Date |
|---:|---|---|---|
| [0001](0001-record-architecture-decisions.md) | Record architecture decisions | Accepted | 2026-07-16 |
| [0002](0002-no-service-registry.md) | No service registry | Accepted | 2026-07-16 |
| [0003](0003-jwt-rs256-jwks.md) | RS256 access tokens with JWKS | Accepted | 2026-07-16 |
| [0004](0004-transactional-outbox-polling.md) | Transactional outbox with polling publishers | Accepted | 2026-07-16 |
| [0005](0005-idempotent-consumer.md) | Idempotent consumers with processed-event ledgers | Accepted | 2026-07-16 |
| [0006](0006-sales-saga-orchestration.md) | Sales inventory saga orchestration | Accepted | 2026-07-16 |
| [0007](0007-domain-service-jwt-jwks.md) | Domain services validate JWT via Identity JWKS | Accepted | 2026-07-16 |
| [0008](0008-authoritative-inventory-reservation-reconciliation.md) | Authoritative Inventory reservation reconciliation | Accepted | 2026-07-20 |
| [0009](0009-persisted-assistant-boundary.md) | Persisted assistant and same-origin web boundary | Accepted | 2026-07-22 |
| [0010](0010-observability-stack.md) | OpenTelemetry observability stack | Accepted | 2026-07-22 |
| [0011](0011-monorepo-strategy.md) | Maven and pnpm monorepo | Accepted | 2026-07-23 |
| [0012](0012-database-per-service.md) | Database per service | Accepted | 2026-07-23 |
| [0013](0013-kafka-event-communication.md) | Kafka for domain event communication | Accepted | 2026-07-23 |
| [0014](0014-api-gateway-and-same-origin-edge.md) | API gateway and same-origin browser edge | Accepted | 2026-07-23 |
| [0015](0015-authenticated-mqtt-iot-ingestion.md) | Authenticated MQTT for IoT ingestion | Accepted | 2026-07-23 |

## Lifecycle

1. Copy [the template](template.md).
2. Use the next four-digit number and a descriptive kebab-case name.
3. Include context, decision, consequences, alternatives, trade-offs, and
   evidence links.
4. Mark the record `Accepted` only after implementation and verification agree.
