# Crop Catalog Service

## Purpose

Owns crops, varieties, and versioned care profiles used by crop planning.
The service has its own PostgreSQL/Flyway history and no Kafka producer or
consumer.

## API

- `/api/v1/crops` and `/api/v1/crops/{cropId}`: create, list, and read crops.
- `/api/v1/crops/by-code/{code}`: code lookup.
- `/api/v1/crops/{cropId}/varieties` and
  `/api/v1/crop-varieties/{varietyId}`: variety management.
- `/api/v1/crops/{cropId}/care-profile` and the guarded admin write path:
  versioned agronomic guidance.

The [OpenAPI contract](../../contracts/openapi/crop-catalog-service.v1.yaml) is
the request/response source of truth.

## Configuration

Database: `agricore_crop_catalog`. Core variables are `CROP_CATALOG_PORT`,
`POSTGRES_*`, `IDENTITY_JWKS_URI`, `JWT_ISSUER`, and `AGRICORE_DEV_MODE`.
Security stays enabled unless explicit local/test dev mode is active.

## Run and verify

```bash
./mvnw -B -pl services/crop-catalog-service -am test
./mvnw -pl services/crop-catalog-service spring-boot:run
```

- Missing seed data: inspect `flyway_schema_history`; seed rows are migrations.
- Duplicate crop or variety code: update the existing record or choose a new
  normalized code.

See [code standards](../../docs/code-standards.md).
