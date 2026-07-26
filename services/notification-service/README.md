# Notification Service

## Purpose

Owns durable notification intent, delivery state, external email delivery, a
persisted administrative in-app inbox, source-event idempotency, and
Notification lifecycle events.

## API and events

- `POST /api/v1/notifications`: guarded direct delivery with optional intent
  idempotency key.
- `GET /api/v1/notifications/in-app`: pageable administrative inbox.
- `PATCH /api/v1/notifications/in-app/{notificationId}/read`: mark an in-app
  entry read.

The direct/inbox API requires `SYSTEM_ADMIN` and
`PERMISSION_NOTIFICATION_ADMIN`; see
[OpenAPI](../../contracts/openapi/notification-service.v1.yaml).

Consumed through one hardened listener:

- Identity `UserRegistered.v1` creates the durable welcome-email intent.
- Sales confirmed/cancelled, Traceability code-generated, and IoT
  threshold/offline events create in-app intents.

The listener validates exact type/version/topic/producer and bounded payload,
persists side effect plus processed marker, and sends invalid contracts directly
to the source DLT. Notification publishes `NotificationRequested.v2`,
`NotificationSent.v2`, and `NotificationFailed.v2` through its outbox.

## Delivery semantics

State is persisted as `REQUESTED`, then ends as `SENT` or `FAILED`. External
EMAIL/SMS uses at-most-once automatic delivery: one provider attempt is made,
and a stale ambiguous `DELIVERING` lease becomes
`FAILED`/`DELIVERY_OUTCOME_UNKNOWN` instead of being resent. `IN_APP` is a
local idempotent write and may be reclaimed within the bounded attempt budget.

## Configuration and verification

Core groups are `NOTIFICATION_SMTP_*`, `NOTIFICATION_DELIVERY_*`,
`NOTIFICATION_KAFKA_*`, `POSTGRES_*`, `KAFKA_BOOTSTRAP_SERVERS`,
`IDENTITY_JWKS_URI`, and `JWT_ISSUER`.

```bash
./mvnw -B -pl services/notification-service -am test
./mvnw -pl services/notification-service spring-boot:run
```

Compose routes SMTP to Mailpit. Before any manual resend of an ambiguous
external delivery, reconcile against provider evidence. See
[Kafka/DLT operations](../../docs/runbooks/kafka-dlq.md) and
[local notification operations](../../docs/runbooks/local-operations.md#notification-delivery).
