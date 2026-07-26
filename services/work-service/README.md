# work-service

## Purpose

Field work tracking: tasks created against a crop cycle, assigned to workers, and completed. Called
by the gateway; publishes task lifecycle events. References cycle and user identifiers by id.

## API surface

- `POST /api/v1/work-tasks` — create a task for a crop cycle
- `GET /api/v1/work-tasks` — list tasks
- `GET /api/v1/work-tasks/{taskId}` — task detail
- `POST /api/v1/work-tasks/{taskId}/assign` — assign to a worker
- `POST /api/v1/work-tasks/{taskId}/complete` — mark complete
- Contract: `contracts/openapi/work-service.v1.yaml`
- Events published: `WorkTaskCreated.v1`, `WorkTaskAssigned.v1`, `WorkTaskCompleted.v1` → `agricore.work.events`
- Events consumed: none

## Env vars

| Name | Required | Default | Description |
|------|----------|---------|-------------|
| `WORK_PORT` | no | `8085` | HTTP listen port |
| `POSTGRES_HOST` | no | `localhost` | Database host |
| `POSTGRES_PORT` | no | `5434` | Database port |
| `POSTGRES_USER` | no | `agricore` | Database user |
| `POSTGRES_PASSWORD` | yes in prod | dev value | Database password |
| `KAFKA_BOOTSTRAP_SERVERS` | no | `localhost:9092` | Broker for the outbox publisher |
| `JWT_ISSUER` | no | `https://agricore.local/identity` | Expected `iss` claim |
| `IDENTITY_JWKS_URI` | no | identity JWKS URL | Key source for local token verification |
| `AGRICORE_DEV_MODE` | no | `false` | Dev shortcuts; keep `false` outside local work |

Database: `agricore_work`.

## Run locally

```bash
./scripts/dev-up.sh
mvn -pl services/work-service spring-boot:run
```

## Test

```bash
./mvnw -B -pl services/work-service -am test
./mvnw -B -pl services/work-service -am verify   # adds the JaCoCo report
```

Target once coverage gating is enforced: ≥ 70% lines / ≥ 65% branches.

## Runbook

- **Task stuck unassigned** — assignment requires a valid worker id; the service does not call identity
  to validate it, so a bad id surfaces as an orphan reference rather than a 404.
- **Events not arriving downstream** — inspect `outbox_events` for `published_at IS NULL` and `last_error`.
- **Reset local data** — drop and recreate `agricore_work`, restart to replay migrations.
