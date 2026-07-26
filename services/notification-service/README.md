# notification-service

## Purpose

Records outbound notifications and their delivery state. Two entry points: a REST endpoint other
services (or operators) call directly, and a Kafka consumer that turns a new user registration into a
welcome notification. Delivery itself is a log sink in this build — no SMTP/webhook adapter is wired.

## API surface

- `POST /api/v1/notifications` — record and "send" a notification (channel, recipient, subject, body)
- Contract: `contracts/openapi/notification-service.v1.yaml`
- Events published: none
- Events consumed: `UserRegistered.v1` from `agricore.identity.events` → welcome notification,
  idempotent per `(eventId, consumer)` via `processed_events`; retries with backoff then routes to
  `agricore.identity.events.DLT`
- `NotificationRequested.v1` is a reserved constant with **no producer**, so nothing consumes it

## Env vars

| Name | Required | Default | Description |
|------|----------|---------|-------------|
| `NOTIFICATION_PORT` | no | `8089` | HTTP listen port |
| `POSTGRES_HOST` | no | `localhost` | Database host |
| `POSTGRES_PORT` | no | `5434` | Database port |
| `POSTGRES_USER` | no | `agricore` | Database user |
| `POSTGRES_PASSWORD` | yes in prod | dev value | Database password |
| `KAFKA_BOOTSTRAP_SERVERS` | no | `localhost:9092` | Broker for the consumer and DLT producer |
| `JWT_ISSUER` | no | `https://agricore.local/identity` | Expected `iss` claim |
| `IDENTITY_JWKS_URI` | no | identity JWKS URL | Key source for local token verification |
| `AGRICORE_DEV_MODE` | no | `false` | Dev shortcuts; keep `false` outside local work |

Database: `agricore_notification` (`notifications`, `processed_events`).

## Run locally

```bash
./scripts/dev-up.sh
mvn -pl services/notification-service spring-boot:run
```

## Test

```bash
./mvnw -B -pl services/notification-service -am test
./mvnw -B -pl services/notification-service -am verify   # adds the JaCoCo report
```

Covers REST recording, envelope parsing and event-type filtering, malformed-message rejection, and
replay idempotency. The listener is exercised directly as a unit, so no broker or Kafka container is
needed; `agricore.kafka.consumer.enabled=false` in the test profile.

## Runbook

- **No welcome notification for a new user** — check identity's `outbox_events` (was the event
  published?), then this service's `processed_events` for the `eventId`, then
  `agricore.identity.events.DLT`.
- **Duplicate notifications** — should be impossible; compare `correlation_id` (it carries the
  `eventId`) against `processed_events`.
- **Replay DLT** — republish onto `agricore.identity.events`; the consumer dedupes on `eventId`.
- **Nothing is actually emailed** — by design. Wiring a real channel means adding an adapter behind
  the application service, not changing the API.
- **Reset local data** — drop and recreate `agricore_notification`, restart to replay migrations.
