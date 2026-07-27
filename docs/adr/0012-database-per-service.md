# 12. Database per service

**Date:** 2026-07-23

**Status:** Accepted

## Context

Farm, crop-cycle, work, harvest, inventory, IoT, sales, traceability, identity,
notification, and assistant data have different invariants and release cadence.
Cross-service table access would bypass those invariants and make independent
migrations unsafe.

## Decision

Every stateful service owns a named PostgreSQL database and its Flyway history.

- Application code connects only to its owning database.
- Cross-service reads use authenticated APIs or local event projections.
- No shared JPA entities, foreign keys across databases, or cross-service SQL
  joins are allowed.
- Local Compose may host the databases in one PostgreSQL-compatible container to
  reduce developer resource use; logical ownership remains separate.
- Production operators may place databases on separate clusters without changing
  application contracts.

## Consequences

### Positive

- Schema changes remain inside one service boundary.
- Authorization and domain invariants cannot be bypassed by a neighboring
  service's SQL.
- Services can choose specialized PostgreSQL capabilities, such as a TimescaleDB
  hypertable for IoT telemetry.

### Negative

- Cross-service reports require APIs, events, or a separate analytics pipeline.
- Referential integrity across services is eventual and must be monitored.
- Local transactions cannot atomically update multiple services.

### Trade-offs

AgriCore accepts duplication in read models and eventual consistency to preserve
ownership and failure isolation. Transactional outbox, idempotent consumers, and
saga compensation cover the resulting consistency boundaries.

## Alternatives considered

- **Shared schema:** rejected because table ownership and migration order become
  ambiguous.
- **One database with schema-per-service:** viable for small deployments, but
  rejected as the architectural contract because credentials could still cross
  boundaries and cluster separation becomes harder.
- **Distributed transactions:** rejected because they couple availability and do
  not remove the need for idempotent recovery.

## References

- [Database initialization](../../infrastructure/docker/postgres/init-databases.sql)
- [System architecture](../architecture/SYSTEM_ARCHITECTURE.md)
- [Transactional outbox ADR](0004-transactional-outbox-polling.md)
