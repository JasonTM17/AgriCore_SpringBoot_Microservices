# Crop Cycle Service

## Purpose

Owns farm-scoped crop cycles, stages, observations, and stage history. It calls
Farm with the caller bearer token to authorize plot scope and publishes
four lifecycle events through a transactional outbox.

## API and events

- `/api/v1/crop-cycles`: create and pageable/list reads.
- `/api/v1/crop-cycles/{cycleId}`: scoped detail.
- `/stage` and `/cancel`: guarded lifecycle transitions.
- `/stage-history` and `/observations`: audit and agronomic observations.

Published to `agricore.crop-cycle.events`:
`CropCycleCreated.v1`, `CropCycleStageChanged.v1`,
`CropCycleCompleted.v1`, and `CropCycleCancelled.v1`.
See [OpenAPI](../../contracts/openapi/crop-cycle-service.v1.yaml) and
[AsyncAPI](../../contracts/asyncapi/agricore-events.yaml).

## Invariants and configuration

PostgreSQL migration V5 installs `btree_gist` and prevents overlapping
`DRAFT`/`ACTIVE` planned date ranges for one plot under concurrency.
Core variables are `CROP_CYCLE_PORT`, `POSTGRES_*`,
`KAFKA_BOOTSTRAP_SERVERS`, `FARM_SERVICE_URL`, `IDENTITY_JWKS_URI`, and
`JWT_ISSUER`.

## Run and verify

```bash
./mvnw -B -pl services/crop-cycle-service -am test
./mvnw -pl services/crop-cycle-service spring-boot:run
```

- `CROP_CYCLE_OVERLAP`: resolve the conflicting active range; never bypass the
  database constraint.
- Farm dependency failure: returns fail-closed `503`, not an unscoped result.
- Event backlog: inspect unpublished `outbox_events` and `last_error`.

See [system architecture](../../docs/architecture/SYSTEM_ARCHITECTURE.md).
