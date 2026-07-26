# Runbook: Kafka Retry and Dead-Letter Topics

## Implemented topology

```text
harvest-service outbox
  -> agricore.harvest.events (HarvestCompleted.v1)
      |- inventory-service consumer
      `- traceability-service consumer

identity-service outbox -> agricore.identity.events (UserRegistered.v1)
  -> notification-service (durable welcome EMAIL delivery)
sales-service outbox -> agricore.sales.events -> notification-service
traceability-service outbox -> agricore.traceability.events -> notification-service
iot-service outbox -> agricore.iot.events -> notification-service
notification-service outbox -> agricore.notification.events

consumer failure
  -> Kafka retry topic <original-topic>-retry-1000
  -> Kafka retry topic <original-topic>-retry-2000
  -> Kafka retry topic <original-topic>-retry-4000
  -> <original-topic>.DLT on recovery

implemented DLTs
  |- agricore.harvest.events.DLT
  |- agricore.identity.events.DLT
  |- agricore.sales.events.DLT
  |- agricore.traceability.events.DLT
  `- agricore.iot.events.DLT
```

The harvest projection consumers use a fixed Spring Kafka non-blocking retry topology with four total attempts (initial delivery plus three retries). Inventory and traceability disable retry-topic auto-creation, so `agricore.harvest.events`, its three exact retry topics, and its DLT must exist before either service starts. `infrastructure/docker/kafka/create-topics.sh` provisions these names for local Compose; production operators must provision the same topology. Producer outboxes for identity, farm, crop-cycle, work, inventory, IoT, traceability, sales, and notification use polling delivery with retryable publish state.

## Consumer error handling

Consumers use Spring Kafka `@RetryableTopic` with bounded exponential backoff. Inventory and traceability declare contract-validation `IllegalArgumentException` directly as an excluded retry type, so the retry-topic recoverer sends those records to the DLT without retry-topic churn:

| Setting | Value |
|---|---:|
| Initial interval | 1,000 ms |
| Multiplier | 2.0 |
| Maximum interval | 4,000 ms |
| Total attempts | 4 (initial + 3 retries) |
| Retry timeout | 30 seconds |
| Recovery destination | `<original-topic>.DLT`; same partition when that DLT partition exists |

Transient processing failures still use all three bounded retry stages. `DeadLetterPublishingRecoverer` publishes to the same partition when the DLT exposes that partition; otherwise its partition verification lets the Kafka producer choose an available partition.

Kafka retry protects event-to-notification persistence. Once an external
EMAIL/SMS provider attempt begins, Notification does not automatically resend
an ambiguous delivery: a stale `DELIVERING` record becomes
`FAILED`/`DELIVERY_OUTCOME_UNKNOWN`. Source-event idempotency prevents a replay
from creating another notification intent, but it cannot prove what an external
provider accepted.

For inventory and traceability, `agricore_kafka_dlq_attempts_total` is instrumented on the actual retry-topic publishing recoverer and increments only when its resolved destination ends in `.DLT`; retry-topic transitions do not increment it. The notification service exposes the same metric name through its service-specific recovery configuration. The counter measures a DLT publish attempt, not DLT topic depth, and does not prove the publish succeeded.

## Retry and DLT retention

The topic provisioning script applies bounded delete retention to retry and dead-letter topics while leaving main-topic retention to the broker or deployment policy:

| Topic class | Default `retention.ms` | Override |
|---|---:|---|
| `*-retry-1000`, `*-retry-2000`, `*-retry-4000` | 86,400,000 (24 hours) | `KAFKA_RETRY_TOPIC_RETENTION_MS` |
| `*.DLT` | 604,800,000 (7 days) | `KAFKA_DLT_TOPIC_RETENTION_MS` |

Both overrides must be positive integer milliseconds. Re-running `create-topics.sh` uses `kafka-configs.sh --alter` to reconcile `cleanup.policy=delete` and `retention.ms` on existing retry and DLT topics; it does not only configure newly created topics.

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

Publisher replicas use `FOR UPDATE SKIP LOCKED`. Farm, crop-cycle, work, and inventory use effective producer defaults of 5 seconds for both `max.block.ms` and `request.timeout.ms`, and 10 seconds for `delivery.timeout.ms`. Their 10-second outbox send wait is not shorter than producer delivery timeout; `max.block.ms` can elapse before the send future is returned, so it is a separate bound rather than part of that wait. A late broker acknowledgement can still produce an at-least-once duplicate, so consumers retain idempotency by stable event ID.

Publishers defer transient broker, timeout, interruption, authentication, and
unknown infrastructure failures with capped exponential backoff indefinitely;
an outage cannot quarantine the backlog. Deterministic producer failures
(`SerializationException`, `RecordTooLargeException`, and
`InvalidTopicException`) quarantine after the configured terminal attempt.
After repairing the payload, topic, or producer contract, operators may release
one reviewed row in its owning service database. Never bulk-release quarantine
or edit an event envelope:

```sql
BEGIN;

SELECT id, aggregate_type, aggregate_id, event_type, topic,
       publish_attempts, last_error, quarantined_at
FROM outbox_events
WHERE id = :event_id
  AND published_at IS NULL
FOR UPDATE;

UPDATE outbox_events
SET quarantined_at = NULL,
    next_attempt_at = CURRENT_TIMESTAMP,
    publish_attempts = 0,
    last_error = NULL
WHERE id = :event_id
  AND published_at IS NULL
  AND quarantined_at IS NOT NULL;

COMMIT;
```

Record the service database, event ID, incident, diagnosis, and operator in the
incident log. A zero-row update means the row is not quarantined or was already
published; stop rather than forcing state. For Harvest completion events, use
the authenticated republish endpoint above because it validates the stored
topic and payload before requeueing.

Sales and notification retain quarantined payloads for seven days by default
before cleanup. Treat that as the operator recovery deadline, or set
`OUTBOX_QUARANTINE_RETENTION` to a longer positive duration independently from
`OUTBOX_RETENTION`.

### Retry-state rollout and rollback

Retry-state writes default to disabled because a previous application version
does not understand `next_attempt_at` or `quarantined_at`. Activate the feature
in two releases for every service that publishes an outbox:

1. Roll out the compatible image to every replica with
   `OUTBOX_PUBLISHER_RETRY_WRITE_STATE_ENABLED=false`.
2. Verify every replica runs the compatible image and Kafka publishing is
   healthy. In a maintenance window, quiesce producer writes and verify no
   unpublished rows remain in each service database.
3. Roll out configuration with
   `--set global.outbox.retry.writeStateEnabled=true` (which renders
   `OUTBOX_PUBLISHER_RETRY_WRITE_STATE_ENABLED=true`), then restore producer
   traffic and verify pending/quarantined metrics. The runtime ConfigMap
   checksum annotation forces this Helm configuration change to restart pods.

Do not enable retry-state writes while any old replica is running: that replica
will ignore the eligibility columns and repeatedly publish deferred or
quarantined rows. After retry-state writes have ever been enabled, rolling back
to an incompatible image is unsafe. Keep a compatible image running, or disable
the publisher, quiesce writes, and make an explicit replay/drop decision for
every unpublished deferred or quarantined row before starting old code. Merely
scaling to zero does not migrate that state.

## DLT response procedure

1. Inspect the DLT matching the failed source topic in Kafka UI or with Kafka tooling: `agricore.harvest.events.DLT`, `agricore.identity.events.DLT`, `agricore.sales.events.DLT`, `agricore.traceability.events.DLT`, or `agricore.iot.events.DLT`.
2. Capture the JSON envelope, original topic/partition/offset headers, and exception headers added by Spring Kafka.
3. Check `agricore_kafka_dlq_attempts_total` and consumer logs for the affected consumer. Do not infer topic depth from the counter.
4. Correct the schema, data, or application cause.
5. Replay the original envelope to its original topic only after confirming the stable `eventId`; inventory, traceability, and notification consumers are idempotent for that ID.
6. Retain or archive the DLT record before the bounded DLT retention window expires when the incident requires longer preservation.

## Recommended thresholds - not provisioned

The repository does not provision Prometheus alert rules or Alertmanager. Candidate operator thresholds:

- `agricore_outbox_backlog > 100` for 5 minutes.
- `agricore_outbox_quarantined > 0` immediately.
- Consumer lag greater than 1,000 records.
- Any increase in `agricore_kafka_dlq_attempts_total`.
- DLT topic depth greater than zero, measured through Kafka consumer-group/topic monitoring rather than the recovery counter.
