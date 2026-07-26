# crop-catalog-service

## Purpose

Reference catalogue of crop varieties (Robusta coffee, Ri6 durian, ST25 rice, and similar) that
crop cycles and harvest batches point at. Read-mostly master data. Called by the gateway and
referenced by crop-cycle-service; calls no other AgriCore service.

## API surface

- `POST /api/v1/crops` — register a crop variety
- `GET /api/v1/crops` — list crops
- `GET /api/v1/crops/{cropId}` — crop detail
- `GET /api/v1/crops/by-code/{code}` — lookup by catalogue code
- Contract: `contracts/openapi/crop-catalog-service.v1.yaml`
- Events published: none (`CropCreated.v1` exists as a constant but this service has no outbox)
- Events consumed: none

## Env vars

| Name | Required | Default | Description |
|------|----------|---------|-------------|
| `CROP_CATALOG_PORT` | no | `8083` | HTTP listen port |
| `POSTGRES_HOST` | no | `localhost` | Database host |
| `POSTGRES_PORT` | no | `5434` | Database port |
| `POSTGRES_USER` | no | `agricore` | Database user |
| `POSTGRES_PASSWORD` | yes in prod | dev value | Database password |
| `JWT_ISSUER` | no | `https://agricore.local/identity` | Expected `iss` claim |
| `IDENTITY_JWKS_URI` | no | identity JWKS URL | Key source for local token verification |
| `AGRICORE_DEV_MODE` | no | `false` | Dev shortcuts; keep `false` outside local work |

Database: `agricore_crop_catalog`. Seed crops load via Flyway migration.

## Run locally

```bash
./scripts/dev-up.sh
mvn -pl services/crop-catalog-service spring-boot:run
```

## Test

```bash
./mvnw -B -pl services/crop-catalog-service -am test
./mvnw -B -pl services/crop-catalog-service -am verify   # adds the JaCoCo report
```

Target once coverage gating is enforced: ≥ 70% lines / ≥ 65% branches.

## Runbook

- **Missing seed crops** — confirm Flyway ran (`flyway_schema_history` in `agricore_crop_catalog`);
  seeds ship inside the migration, not a startup hook.
- **Crop code conflicts** — codes are unique; resolve by patching the existing row rather than
  inserting a duplicate variety.
- **Reset local data** — drop and recreate `agricore_crop_catalog`, restart to replay migrations.
