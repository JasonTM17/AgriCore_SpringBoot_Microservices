# harvest-service

## Purpose

Records harvest batches and the moment a harvest completes. Its `HarvestCompleted.v1` event is the
fan-out point of the platform: inventory turns it into stock, traceability turns it into a public QR
record. Called by the gateway; publishes through a transactional outbox.

## API surface

- `POST /api/v1/harvests` — open a harvest batch for a crop cycle
- `POST /api/v1/harvests/complete` — complete a harvest (net weight, quality grade, warehouse)
- `GET /api/v1/harvests/{harvestId}` — harvest detail
- Contract: `contracts/openapi/harvest-service.v1.yaml`
- Events published: `HarvestCompleted.v1` (also `HarvestStarted.v1`, `HarvestBatchCreated.v1` constants)
  → `agricore.harvest.events`
- Events consumed: none

## Env vars

| Name | Required | Default | Description |
|------|----------|---------|-------------|
| `HARVEST_PORT` | no | `8087` | HTTP listen port |
| `POSTGRES_HOST` | no | `localhost` | Database host |
| `POSTGRES_PORT` | no | `5434` | Database port |
| `POSTGRES_USER` | no | `agricore` | Database user |
| `POSTGRES_PASSWORD` | yes in prod | dev value | Database password |
| `KAFKA_BOOTSTRAP_SERVERS` | no | `localhost:9092` | Broker for the outbox publisher |
| `JWT_ISSUER` | no | `https://agricore.local/identity` | Expected `iss` claim |
| `IDENTITY_JWKS_URI` | no | identity JWKS URL | Key source for local token verification |
| `AGRICORE_DEV_MODE` | no | `false` | Dev shortcuts; keep `false` outside local work |

Database: `agricore_harvest`.

## Run locally

```bash
./scripts/dev-up.sh
mvn -pl services/harvest-service spring-boot:run
```

## Test

```bash
./mvnw -B -pl services/harvest-service -am test
./mvnw -B -pl services/harvest-service -am verify   # adds the JaCoCo report
```

The full chain (harvest → Kafka → inventory + traceability) is verified by
`scripts/e2e-happy-path.ps1` against the compose stack, which writes an evidence bundle.

## Runbook

- **Stock did not move after a harvest** — check `outbox_events` here first (`published_at IS NULL`),
  then the inventory consumer's `processed_events` and the `agricore.harvest.events.DLT` topic.
- **Duplicate stock-in** — should be impossible: inventory dedupes on `eventId`. If it happened, compare
  `eventId` values in inventory's `processed_events`.
- **Replay a harvest event** — republish the envelope from `outbox_events.payload`; consumers are
  idempotent, so a replay is safe.
- **Reset local data** — drop and recreate `agricore_harvest`, restart to replay migrations.
