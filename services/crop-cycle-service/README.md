# crop-cycle-service

## Purpose

Owns the growing cycle: which crop is planted on which plot, what stage it is in, and when it
completes. The spine between farm geography and field work. Called by the gateway; publishes cycle
events consumed downstream. References farm and crop identifiers by id, without cross-service joins.

## API surface

- `POST /api/v1/crop-cycles` — start a cycle for a plot + crop
- `GET /api/v1/crop-cycles` — list cycles
- `GET /api/v1/crop-cycles/{cycleId}` — cycle detail
- `POST /api/v1/crop-cycles/{cycleId}/stage` — advance or change stage
- Contract: `contracts/openapi/crop-cycle-service.v1.yaml`
- Events published: `CropCycleCreated.v1`, `CropCycleStageChanged.v1`, `CropCycleCompleted.v1`,
  `CropCycleCancelled.v1` → `agricore.crop-cycle.events`
- Events consumed: none

## Env vars

| Name | Required | Default | Description |
|------|----------|---------|-------------|
| `CROP_CYCLE_PORT` | no | `8084` | HTTP listen port |
| `POSTGRES_HOST` | no | `localhost` | Database host |
| `POSTGRES_PORT` | no | `5434` | Database port |
| `POSTGRES_USER` | no | `agricore` | Database user |
| `POSTGRES_PASSWORD` | yes in prod | dev value | Database password |
| `KAFKA_BOOTSTRAP_SERVERS` | no | `localhost:9092` | Broker for the outbox publisher |
| `JWT_ISSUER` | no | `https://agricore.local/identity` | Expected `iss` claim |
| `IDENTITY_JWKS_URI` | no | identity JWKS URL | Key source for local token verification |
| `AGRICORE_DEV_MODE` | no | `false` | Dev shortcuts; keep `false` outside local work |

Database: `agricore_crop_cycle`.

## Run locally

```bash
./scripts/dev-up.sh
mvn -pl services/crop-cycle-service spring-boot:run
```

## Test

```bash
./mvnw -B -pl services/crop-cycle-service -am test
./mvnw -B -pl services/crop-cycle-service -am verify   # adds the JaCoCo report
```

Characterization tests lock the exact created / stage-changed / completed envelopes and assert that
a rejected transition writes no outbox row. Envelope construction lives in `CropCycleOutboxWriter`
so the application service stays under the modularization threshold.

## Runbook

- **Stage transition rejected** — the domain guards illegal stage order; read the returned error code
  rather than editing the row directly.
- **Events not arriving downstream** — inspect `outbox_events` for `published_at IS NULL` plus
  `last_error`; the publisher is property-gated by `agricore.outbox.publisher.enabled`.
- **Envelope change** — treat any field rename as a breaking change: consumers parse these names, and
  the characterization tests will fail first.
- **Reset local data** — drop and recreate `agricore_crop_cycle`, restart to replay migrations.
