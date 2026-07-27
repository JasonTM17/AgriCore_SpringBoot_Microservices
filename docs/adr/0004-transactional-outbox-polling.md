# 4. Transactional outbox with polling publishers

**Date:** 2026-07-16

**Status:** Accepted

## Context

Writing a service database and publishing Kafka in one request creates a dual
write: either side can succeed while the other fails.

## Decision

Write the aggregate mutation and versioned event envelope to an outbox table in
one local database transaction. A scheduled publisher locks bounded unpublished
batches, sends with bounded producer timeouts, marks acknowledged rows, and
persists failure attempts for repair. Cleanup removes only sufficiently old
published rows.

Polling remains the initial transport. Debezium CDC may supersede it when
measured throughput or latency justifies another platform component.

## Consequences

### Positive

- A committed business change always leaves durable publication evidence.
- Broker outages do not roll back already-accepted domain commands.
- Backlog and failure state are observable and repairable.

### Negative

- Events appear after a polling delay.
- Every producer owns a poller, index, cleanup policy, and operational backlog.
- Publication is at least once; consumers must be idempotent.

### Neutral

- The pattern guarantees durable intent, not exactly-once end-to-end delivery.

## Trade-offs

The project accepts small publication latency and outbox storage in exchange for
removing the database/Kafka dual-write failure window.

## Alternatives considered

- **Publish inside the business transaction:** rejected because Kafka cannot
  participate safely in the local database commit.
- **Publish after commit without an outbox:** rejected because a process crash
  loses the event.
- **Debezium CDC:** deferred until its additional connectors and operations are
  justified by measured need.
- **Distributed transaction:** rejected because it couples availability and is
  not supported consistently across the chosen stack.

## References

- [Outbox flow diagram](../diagrams/transactional-outbox-flow.md)
- [Kafka communication ADR](0013-kafka-event-communication.md)
- [Local operations](../runbooks/local-operations.md)
