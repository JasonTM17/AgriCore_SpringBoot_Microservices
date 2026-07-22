# Runbook: Kafka Retry and Dead-Letter Topics

## Implemented topology

```text
harvest-service outbox
  → agricore.harvest.events (`HarvestCompleted.v1`)
      ├─ inventory-service consumer
      └─ traceability-service consumer

consumer failure
  → in-process exponential retry
  → agricore.harvest.events.DLT on recovery
```

Only the `HarvestCompleted.v1` path is implemented. There are no retry topics, and this DLT flow is not implemented for farm, crop-cycle, or work topics.

## Consumer error handling

Both consumers use Spring Kafka `DefaultErrorHandler` with `ExponentialBackOff`:

| Setting | Value |
|---|---:|
| Initial interval | 500 ms |
| Multiplier | 2.0 |
| Maximum elapsed time | 8 seconds |
| Recovery destination | `<original-topic>.DLT`; same partition when that DLT partition exists |

Both consumers classify contract-validation `IllegalArgumentException` as non-retryable and hand it directly to recovery. Transient processing failures use the bounded backoff. `DeadLetterPublishingRecoverer` publishes to the same partition when the DLT exposes that partition; otherwise its partition verification lets the Kafka producer choose an available partition.

`agricore_kafka_dlq_attempts_total{consumer="inventory-service|traceability-service"}` increments when a record is handed to dead-letter recovery. It does not measure DLT topic depth and does not prove the DLT publish succeeded.

## Producer outbox path

Harvest completion writes an `outbox_events` row in the business transaction. `OutboxPublisher` polls and publishes it to `agricore.harvest.events`. Failed publish attempts update `publish_attempts` and `last_error`.

Operators can inspect a completion event with:

```text
GET /api/v1/harvests/{harvestId}/completion-event
```

After correcting the producer-side cause, an authorized operator can requeue the original row and envelope:

```text
POST /api/v1/harvests/{harvestId}/completion-event/republish
```

The endpoint returns `202`, preserves the event ID, and is a no-op while the event is already pending. Stored topic, envelope metadata, and projection-critical payload are checked before requeue. Corrupt rows return `409`; concurrent repair lock contention returns retryable `503`.

Publisher replicas use `FOR UPDATE SKIP LOCKED`. `KafkaTemplate.send()` is bounded by `KAFKA_PRODUCER_MAX_BLOCK_MS` (5 seconds by default) and then `OUTBOX_PUBLISHER_SEND_TIMEOUT_MS` (10 seconds by default). A late broker acknowledgement can still produce an at-least-once duplicate, so consumers retain idempotency by stable event ID.

## DLT response procedure

1. Inspect `agricore.harvest.events.DLT` in Kafka UI or with Kafka tooling.
2. Capture the JSON envelope, original topic/partition/offset headers, and exception headers added by Spring Kafka.
3. Check `agricore_kafka_dlq_attempts_total` and consumer logs for the affected consumer. Do not infer topic depth from the counter.
4. Correct the schema, data, or application cause.
5. Replay the original envelope to `agricore.harvest.events` only after confirming the stable `eventId`; inventory and traceability are idempotent for that ID.
6. Retain or archive the DLT record according to the deployment's Kafka retention policy.

## Recommended thresholds — not provisioned

The repository does not provision Prometheus alert rules or Alertmanager. Candidate operator thresholds:

- `agricore_outbox_backlog > 100` for 5 minutes.
- Consumer lag greater than 1,000 records.
- Any increase in `agricore_kafka_dlq_attempts_total`.
- DLT topic depth greater than zero, measured through Kafka consumer-group/topic monitoring rather than the recovery counter.
