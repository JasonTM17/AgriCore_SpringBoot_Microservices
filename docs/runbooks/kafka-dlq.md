# Runbook: Kafka Retry & Dead Letter Topics

## Topology

```text
agricore.harvest.events
  → consumer inventory-service
  → on transient failure: retry (app-level / outbox republish)
  → after max attempts: agricore.harvest.events.DLQ
```

Suggested topics (create in production):

| Topic | Purpose |
|-------|---------|
| `agricore.harvest.events` | Main harvest domain events |
| `agricore.harvest.events.retry-1` | First retry (delay 30s) |
| `agricore.harvest.events.retry-2` | Second retry (delay 2m) |
| `agricore.harvest.events.retry-3` | Third retry (delay 10m) |
| `agricore.harvest.events.DLQ` | Dead letter after max retries |

Same pattern for `agricore.farm.events`, `agricore.crop-cycle.events`, `agricore.work.events`.

## Outbox path (current implementation)

Harvest service writes to `outbox_events` then `OutboxPublisher` polls and publishes.
Failed publishes increment `publish_attempts` and set `last_error` (no infinite silent fail).

## Consumer path

Inventory consumer is idempotent via `processed_events`.
On processing exception, message is not acknowledged (Spring Kafka default redelivery).

## Operator steps for DLQ

1. Inspect DLQ message headers (`eventId`, `exception`, `original-topic`).
2. Fix root cause (schema, data, downstream).
3. Replay to main topic if safe (same `eventId` — consumer must be idempotent).
4. Delete or archive DLQ after resolution.

## Alerts

- Outbox unpublished count > 100 for 5 minutes
- Consumer lag > 1000
- DLQ message count > 0
