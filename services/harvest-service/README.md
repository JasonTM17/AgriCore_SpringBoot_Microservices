# Harvest Service

## Purpose

Owns farm-scoped harvest batches and their lifecycle. It verifies the caller's
plot through Farm, verifies crop-cycle farm/plot scope through Crop Cycle, and
publishes lifecycle events through a transactional outbox.

## API and events

- `/api/v1/harvests`: create a batch.
- `/api/v1/harvests/complete` and
  `/api/v1/harvests/{harvestId}/complete`: completion paths retained by the
  contract.
- `/api/v1/harvests/{harvestId}`: scoped detail.
- `/completion-event` and `/completion-event/republish`: inspect and safely
  requeue the original completion envelope.

Published to `agricore.harvest.events`: `HarvestBatchCreated.v1`,
`HarvestStarted.v1`, and farm-scoped `HarvestCompleted.v1`. Inventory and
Traceability consume completion idempotently. See
[OpenAPI](../../contracts/openapi/harvest-service.v1.yaml) and
[AsyncAPI](../../contracts/asyncapi/agricore-events.yaml).

## Configuration

Database: `agricore_harvest`. Core variables are `HARVEST_PORT`, `POSTGRES_*`,
`KAFKA_BOOTSTRAP_SERVERS`, `FARM_SERVICE_URL`,
`CROP_CYCLE_SERVICE_URL`, `IDENTITY_JWKS_URI`, and `JWT_ISSUER`.

## Run and verify

```bash
./mvnw -B -pl services/harvest-service -am test
./mvnw -pl services/harvest-service spring-boot:run
```

- Projection absent: check completion outbox state, consumer ledgers, then the
  Harvest DLT.
- Republish preserves the stable event ID; do not synthesize a replacement
  event for repair.
- Farm/plot/cycle mismatch is masked or fails closed before mutation.

See the [harvest event flow](../../docs/diagrams/harvest-event-flow.md).
