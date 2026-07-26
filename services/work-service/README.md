# Work Service

## Purpose

Owns farm-scoped work tasks, assignments, executions, material use, and private
task attachments. It authorizes plot scope through Farm, consumes material
through Inventory's internal boundary, and stores validated evidence through a
MinIO/S3-compatible adapter.

## API and events

The [OpenAPI contract](../../contracts/openapi/work-service.v1.yaml) covers task
create/list/detail, assign, assignment/execution history, start/complete/cancel,
attachment upload/list, and guarded attachment download.

Published through the outbox: `WorkTaskCreated.v1`, `WorkTaskAssigned.v1`,
`WorkTaskCompleted.v1`, and `MaterialConsumed.v1`. Work consumes no Kafka
events.

## Configuration

Database: `agricore_work`. Core groups are `OBJECT_STORAGE_*`,
`WORK_ATTACHMENT_*`, `FARM_SERVICE_*`, `INVENTORY_SERVICE_*`,
`INVENTORY_INTERNAL_SERVICE_TOKEN`, `POSTGRES_*`,
`KAFKA_BOOTSTRAP_SERVERS`, `IDENTITY_JWKS_URI`, and `JWT_ISSUER`.
Object storage is disabled unless explicitly configured; Compose enables the
private MinIO path.

## Run and verify

```bash
./mvnw -B -pl services/work-service -am test
./mvnw -pl services/work-service spring-boot:run
```

- Farm denial/unavailability prevents task and attachment mutation.
- Attachment type, size, digest, count, object host, and download expiry are
  bounded; never expose a permanent public bucket URL.
- Material consumption failures must not be hidden as completed task work.

See [AsyncAPI](../../contracts/asyncapi/agricore-events.yaml),
[authorization model](../../docs/security/microservices-authz.md), and
[local MinIO operations](../../docs/runbooks/local-operations.md).
