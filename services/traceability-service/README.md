# Traceability Service

## Purpose

Owns the public QR read model. It consumes farm-scoped Harvest completion
events into its own database, so public scans never join Farm, Harvest,
Inventory, or Work databases.

## API and events

- `GET /public/api/v1/traceability/{traceabilityCode}`: public-safe batch
  projection.
- `GET /public/api/v1/traceability/{traceabilityCode}/qr`: QR image.
- `GET /api/v1/traceability/events/harvest-completed/{eventId}/acknowledgement`:
  guarded projection acknowledgement.

There is no general authenticated manual batch-write endpoint. The
[OpenAPI contract](../../contracts/openapi/traceability-service.v1.yaml) is
authoritative.

Consumed: `HarvestCompleted.v1` with processed marker and projection in one
transaction. Published through the outbox: `TraceabilityBatchCreated.v1` and
`TraceabilityCodeGenerated.v1`; Notification consumes code generation.

## Configuration

Database: `agricore_traceability`. Core variables are `TRACEABILITY_PORT`,
`TRACEABILITY_PUBLIC_BASE_URL`, `POSTGRES_*`, `KAFKA_BOOTSTRAP_SERVERS`,
`IDENTITY_JWKS_URI`, and `JWT_ISSUER`.

## Run and verify

```bash
./mvnw -B -pl services/traceability-service -am test
./mvnw -pl services/traceability-service spring-boot:run
```

- Public `404`: projection may still be pending; check processed-event state and
  the Harvest DLT.
- A public response exposing internal identifiers, users, customers, or prices
  is a security defect.
- Replay only the original envelope/event ID; projection is idempotent.

See the [traceability flow](../../docs/diagrams/traceability-data-flow.md) and
[Kafka runbook](../../docs/runbooks/kafka-dlq.md).
