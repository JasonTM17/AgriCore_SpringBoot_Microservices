# 5. Idempotent consumers with processed-event ledgers

**Date:** 2026-07-16

**Status:** Accepted

## Context

Kafka is at least once. Redelivery after a consumer or broker failure must not
double stock, duplicate a public batch, or resend the same notification effect.

## Decision

Each side-effecting consumer stores a stable event marker in its owning database.
The event marker and business side effect commit in one transaction. The marker
key includes the consumer identity and, where needed, authoritative farm or
warehouse scope. Replaying an exact processed event returns the stored outcome
without repeating the mutation.

`HarvestCompleted.v1` carries required `farmId` and `warehouseId`. Inventory
stores both on new processed markers and checks the caller-supplied warehouse
farm before exposing acknowledgement state. Nullable scope columns preserve
upgrade compatibility for older markers, but legacy rows fail closed until an
operator maps them.

Invalid types, unsupported versions, and malformed envelopes do not create a
processed marker; they follow the bounded retry/DLT policy.

## Consequences

### Positive

- Exact event redelivery is safe and independently verifiable.
- Consumer state and side effects cannot commit separately.

### Negative

- Every envelope needs a stable event ID.
- Ledgers need indexes, retention policy, and scope-aware migrations.
- Idempotency does not make semantically different events with different IDs
  equivalent.

### Neutral

- The same application use case may be exercised by a guarded test/repair
  endpoint, but production Kafka acknowledgement remains adapter-owned.

## Trade-offs

The platform accepts a small durable ledger per consumer to avoid relying on
broker offsets as proof that a database side effect committed.

## Alternatives considered

- **Kafka offset only:** rejected because an offset and a database transaction
  are separate commits.
- **Exactly-once Kafka transactions:** rejected because they do not atomically
  include arbitrary service databases.
- **In-memory duplicate cache:** rejected because restart and eviction lose
  evidence.
- **Natural-key checks only:** insufficient for event replay diagnostics and
  cannot cover every side effect.

## References

- [Harvest event flow](../diagrams/harvest-event-flow.md)
- [Kafka DLT runbook](../runbooks/kafka-dlq.md)
- [Kafka communication ADR](0013-kafka-event-communication.md)
