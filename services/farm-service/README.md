# farm-service

## Purpose

Owns farms and their plots — the physical geography every downstream domain references. Called by
the gateway; publishes farm/plot events for other services. Calls no other AgriCore service.

## API surface

- `POST /api/v1/farms` — create a farm
- `GET /api/v1/farms` — list farms
- `GET /api/v1/farms/{farmId}` — farm detail
- `PATCH /api/v1/farms/{farmId}` — update a farm
- `POST /api/v1/farms/{farmId}/plots` — add a plot to a farm
- `GET /api/v1/farms/{farmId}/plots` — list a farm's plots
- `GET /api/v1/plots/{plotId}` — plot detail
- `PATCH /api/v1/plots/{plotId}` — update plot (including status)
- Contract: `contracts/openapi/farm-service.v1.yaml`
- Events published: `FarmCreated.v1`, `PlotCreated.v1`, `PlotStatusChanged.v1` → `agricore.farm.events`
- Events consumed: none

## Env vars

| Name | Required | Default | Description |
|------|----------|---------|-------------|
| `FARM_PORT` | no | `8082` | HTTP listen port |
| `POSTGRES_HOST` | no | `localhost` | Database host |
| `POSTGRES_PORT` | no | `5434` | Database port |
| `POSTGRES_USER` | no | `agricore` | Database user |
| `POSTGRES_PASSWORD` | yes in prod | dev value | Database password |
| `KAFKA_BOOTSTRAP_SERVERS` | no | `localhost:9092` | Broker for the outbox publisher |
| `JWT_ISSUER` | no | `https://agricore.local/identity` | Expected `iss` claim |
| `IDENTITY_JWKS_URI` | no | identity JWKS URL | Key source for local token verification |
| `AGRICORE_DEV_MODE` | no | `false` | Dev shortcuts; keep `false` outside local work |

Database: `agricore_farm` (own schema, Flyway-managed).

## Run locally

```bash
./scripts/dev-up.sh
mvn -pl services/farm-service spring-boot:run
```

## Test

```bash
./mvnw -B -pl services/farm-service -am test
./mvnw -B -pl services/farm-service -am verify   # adds the JaCoCo report
```

Covers farm/plot rules and the outbox rows written on create/status change. Target once coverage
gating is enforced: ≥ 70% lines / ≥ 65% branches.

## Runbook

- **Events not arriving downstream** — inspect `outbox_events` for `published_at IS NULL`; the
  polling publisher logs `last_error` per row. Publisher is disabled when
  `agricore.outbox.publisher.enabled=false`.
- **Reset local data** — drop and recreate the `agricore_farm` database, then restart; Flyway replays migrations.
- **Drain and restart** — plot/farm writes are short transactions; a rolling restart needs no draining,
  and unpublished outbox rows are picked up after boot.
