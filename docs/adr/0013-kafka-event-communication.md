# 13. Kafka for domain event communication

**Date:** 2026-07-23

**Status:** Accepted

## Context

Harvest projection, notifications, and other downstream reactions must not make
the originating HTTP request depend on every consumer. Events need replayable,
versioned contracts and at-least-once delivery semantics.

## Decision

Use Kafka for implemented cross-service domain events.

- Every message uses the common envelope with event ID, type, version, UTC
  occurrence time, trace/correlation/causation identifiers, producer, and payload.
- JSON Schema files are immutable by version and are referenced by AsyncAPI.
- Producers write a transactional outbox before publication.
- Consumers validate exact event type and version, persist processed-event
  markers with side effects, and route exhausted or invalid records to a DLT.
- Topic creation, retry topology, and local broker configuration remain explicit
  infrastructure assets.
- REST remains appropriate for synchronous authorization and reservation
  decisions that require an immediate caller result.

## Consequences

### Positive

- Producers are decoupled from consumer latency and temporary outages.
- Stable event IDs support replay and duplicate suppression.
- Schemas and AsyncAPI make compatibility review mechanical.

### Negative

- Delivery is eventually consistent and can be duplicated.
- Operators must monitor lag, outbox backlog, retry traffic, and DLT recovery.
- Schema evolution and topic retention require governance.

### Trade-offs

The platform accepts operational Kafka complexity in exchange for durable,
replayable integration. It deliberately keeps farm authorization and the Sales
reservation decision synchronous where eventual feedback would harm the command
contract.

## Alternatives considered

- **Point-to-point REST callbacks:** rejected because producer availability would
  depend on every downstream service and replay would require custom storage.
- **RabbitMQ:** viable for work queues, but Kafka better matches durable event
  replay and the requested architecture.
- **Database polling across service schemas:** rejected because it violates
  database ownership.
- **Kafka for every call:** rejected because authorization and immediate
  reservation outcomes are clearer as bounded REST calls.

## References

- [AsyncAPI event contract](../../contracts/asyncapi/agricore-events.yaml)
- [Event schemas](../../contracts/event-schemas/)
- [Kafka retry runbook](../runbooks/kafka-dlq.md)
- [Outbox ADR](0004-transactional-outbox-polling.md)
- [Idempotent consumer ADR](0005-idempotent-consumer.md)
