# AgriCore – Agricultural Enterprise Management Platform

AgriCore is a Java 21 and Spring Boot microservices platform for farm operations: farms, crop catalog, crop cycles, field work, harvest, inventory, IoT, sales, notifications, QR traceability, and a bounded assistant. A React console provides the browser interface through a same-origin Nginx edge.

## Status

Pre-release implementation: the repository contains 13 Spring applications, the React console, local Compose stacks, a Helm application chart, automated quality and security gates, and a gated Docker Hub publishing workflow. The implemented event mesh currently covers 29 versioned Kafka events with transactional outboxes, idempotent consumers, bounded DLT recovery, and contract checks; this status does not claim a production installation.

| Application | Port | Image |
|---|---:|---|
| API gateway | 8080 | `nguyenson1710/agricore-gateway` |
| Identity service | 8081 | `nguyenson1710/agricore-identity` |
| Farm service | 8082 | `nguyenson1710/agricore-farm` |
| Crop catalog service | 8083 | `nguyenson1710/agricore-crop-catalog` |
| Crop cycle service | 8084 | `nguyenson1710/agricore-crop-cycle` |
| Work service | 8085 | `nguyenson1710/agricore-work` |
| Inventory service | 8086 | `nguyenson1710/agricore-inventory` |
| Harvest service | 8087 | `nguyenson1710/agricore-harvest` |
| Notification service | 8089 | `nguyenson1710/agricore-notification` |
| IoT service | 8090 | `nguyenson1710/agricore-iot` |
| Sales service | 8091 | `nguyenson1710/agricore-sales` |
| Traceability service | 8092 | `nguyenson1710/agricore-traceability` |
| Assistant service | 8093, internal | `nguyenson1710/agricore-assistant` |
| React console | 3000 on host | `nguyenson1710/agricore-console` |

Published image tags are `latest`, the seven-character commit SHA, and the full commit SHA.

## Architecture

```text
Browser :3000 ── Nginx ── /api, /public/api ── API Gateway :8080
                                                ├─ Identity, Farm, Catalog
                                                ├─ Cycle, Work, Harvest, Inventory
                                                └─ Traceability, IoT, Sales, Notification, Assistant

Harvest transactional outbox ── Kafka ── Inventory + Traceability
Sales ── synchronous inventory reservation saga
```

- Database per service with PostgreSQL and Flyway.
- Kafka event mesh: Harvest feeds Inventory and Traceability; Sales, Traceability, and IoT feed Notification; every event producer uses a transactional outbox.
- Transactional outbox publishers across farm, crop-cycle, work, harvest, inventory, IoT, traceability, sales, and notification.
- Idempotent `HarvestCompleted.v1` consumers in inventory and traceability, plus notification consumers for sales, traceability, and IoT events.
- Versioned JSON Schema and AsyncAPI contracts for 29 implemented domain events.
- RS256 JWTs, JWKS validation, role checks, permission-authority plumbing, and farm membership authorization.
- Persisted assistant with authenticated replayable SSE, read-only farm tools, and Redis-backed budgets.

See [System Architecture](docs/architecture/SYSTEM_ARCHITECTURE.md), [ADRs](docs/adr/), and [local operations](docs/runbooks/local-operations.md).

## Prerequisites

- JDK 21+
- Maven 3.9+ or the included Maven wrapper
- Node.js 22.13.0+
- pnpm 11+
- Docker with Docker Compose
- OpenSSL for local JWT key generation

## Quick start

Create local configuration and JWT keys once:

```powershell
Copy-Item .env.example .env
.\scripts\generate-jwt-keys.ps1
```

Start infrastructure first so Compose creates the `agricore_default` network, then observability, then the applications:

```powershell
docker compose up -d postgres redis kafka kafka-ui kafka-topics-init mqtt minio
docker compose -f docker-compose.observability.yml up -d
docker compose up -d --build
```

| Endpoint | URL |
|---|---|
| Console | `http://localhost:3000` |
| Gateway | `http://localhost:8080` |
| Kafka UI | `http://localhost:8088` |
| Mailpit | `http://localhost:8025` |
| MQTT broker | `tcp://localhost:1883` |
| MinIO API | `http://localhost:9000` |
| MinIO console | `http://localhost:9001` |
| Grafana | `http://localhost:3001` |
| Prometheus | `http://localhost:9090` |
| Tempo | `http://localhost:3200` |

The assistant is not published directly. Use the console or gateway route `/api/v1/assistant/**`. `ASSISTANT_PROVIDER=none` is the safe default; provider type, model, base URL, and API key belong only in a local `.env`, secret manager, or Kubernetes Secret.

Local email delivery uses Mailpit. Notification state is persisted as `REQUESTED` before bounded delivery attempts and ends as `SENT` or `FAILED`; captured development email is visible in the Mailpit UI.

IoT devices publish authenticated QoS 1 JSON to `agricore/telemetry/{deviceCode}/reading`. Every MQTT payload requires a stable `readingId`, and `iot-service` deduplicates redelivery by that ID while rejecting ID reuse with different telemetry. Local Mosquitto bootstraps non-anonymous service/device users from environment variables and restricts each device user to its own topic. Run a deterministic simulator with `./scripts/sensor-simulator.ps1 -DeviceCount 3 -Iterations 10 -FrequencySeconds 2 -MinimumValue 30 -MaximumValue 70 -AnomalyProbabilityPercent 20 -Seed 42`; the POSIX equivalent is the `iot-mqtt-simulator` Compose profile. Register the mapped device codes first. Production must provide authenticated TLS plus broker ACLs managed outside this repository.

For an existing PostgreSQL volume, follow the [assistant database provisioning runbook](docs/runbooks/assistant-database-provisioning.md).

## Build and verification

Backend:

```bash
./mvnw -B verify
```

Frontend:

```bash
pnpm install --frozen-lockfile
pnpm contracts:check
pnpm lint
pnpm typecheck
pnpm test
pnpm build
```

The CI browser gate additionally installs Playwright Chromium and runs `pnpm e2e`.

Full platform verification builds and starts the application stack, runs Maven tests, and executes the gateway JWT happy path:

```powershell
.\scripts\verify-platform.ps1 -EvidenceDir C:\path\to\evidence
```

```bash
export EVIDENCE_DIR=./.verify-evidence
./scripts/verify-platform.sh
```

For an already-running stack:

```powershell
.\scripts\e2e-happy-path.ps1 -EvidenceDir C:\path\to\evidence
```

## Observability

```text
13 Spring applications ── /actuator/prometheus ── Prometheus ── Grafana
        │
        └─ Micrometer tracing bridge ── OTLP/HTTP ── Tempo ─────┘

Application logs ── ECS JSON ── container stdout ── Alloy ── Loki ── Grafana
```

Local Compose exports traces to `http://tempo:4318/v1/traces` with sampling probability `1.0`. Prometheus scrapes all 13 Spring applications. Grafana provisions Prometheus, Tempo, and Loki datasources plus seven read-only dashboards. Custom meters cover transactional outbox backlog, Kafka dead-letter recovery attempts, harvest processing latency, inventory outcomes, IoT ingestion and alerts, sales sagas, notification delivery, and assistant generations.

Alloy discovers only this project's Docker containers, enriches their structured stdout, and forwards it to persistent local Loki storage. Loki keeps 72 hours of local logs, while Docker files are independently bounded to three 10 MiB files per container by default. MinIO provides persistent, loopback-bound local object storage; application media integration is documented separately as it is delivered. See [local operations](docs/runbooks/local-operations.md) for verification commands and the exact metric catalog.

## Authentication example

```bash
curl -s -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"manager@agricore.local","password":"Secret123!","fullName":"Farm Manager"}'

curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"manager@agricore.local","password":"Secret123!"}'
```

JWKS: `GET /.well-known/jwks.json`

Default roles: `SYSTEM_ADMIN`, `FARM_MANAGER`, `AGRONOMIST`, `FIELD_WORKER`, `WAREHOUSE_MANAGER`, `SALES_STAFF`, and `AUDITOR`. New users receive `FIELD_WORKER`; administrators manage roles through `PATCH /api/v1/admin/users/{id}/roles`.

### Permission administration

Identity owns the permission catalog and role grants. The Identity OpenAPI 1.3.0 contract documents these `SYSTEM_ADMIN`-only routes:

| Route | Behavior |
|---|---|
| `GET /api/v1/admin/permissions` | List the permission catalog. |
| `POST /api/v1/admin/permissions` | Create a uniquely coded permission. |
| `GET /api/v1/admin/roles/{roleCode}/permissions` | Read a role's grants. |
| `PUT /api/v1/admin/roles/{roleCode}/permissions` | Atomically replace a role's grants; unknown codes leave existing grants unchanged. |

New access tokens include `permissions`, the sorted distinct union granted through the user's roles. Tokens are snapshots: a grant change appears only in a newly issued access token, such as after login or refresh; the old token keeps its previous claims until it expires (900 seconds by default). Identity, the gateway, and servlet domain services map valid claim entries to `ROLE_*` and `PERMISSION_*` authorities, but current endpoint policies remain role-based. No permission catalog seed, permission UI, or production `hasAuthority("PERMISSION_*")` guard exists yet. See the [authorization model](docs/security/microservices-authz.md) and [Identity contract](contracts/openapi/identity-service.v1.yaml).

## Helm deployment scope

The chart at `infrastructure/helm/agricore` renders Deployments and Services for all 13 Spring applications and the console, plus an optional same-origin Ingress. It also includes an idempotent pre-install/pre-upgrade Job for the assistant database.

The chart does not install PostgreSQL, Redis, Kafka, MinIO, Tempo, Prometheus, Loki, Alloy, or Grafana. Operators must provide those dependencies and create the database credential Secret named by `postgres.databaseSecretName`. Configure the notification chart's external SMTP host, TLS, and username/password Secret before enabling delivery; SMTP defaults are intentionally placeholders. OTLP trace export is disabled until `observability.otlpTracingEndpoint` is set; the chart's sampling default is `0.1`.

## CI, security, and publishing

GitHub Actions define these release gates:

- `ci.yml`: Maven `verify`; generated contract drift check; frontend lint, typecheck, unit tests, production build, and Playwright journeys; Gitleaks; Compose validation; Helm lint and render.
- `codeql.yml`: scheduled and push/PR Java CodeQL analysis.
- `trivy.yml`: scheduled and push/PR filesystem scan that fails on fixable high or critical findings.
- `docker-publish.yml`: publishes the 13 Spring images and console image only for an eligible successful default-branch CI revision, or an eligible manual default-branch dispatch.

Docker Hub credentials must be repository secrets named `DOCKERHUB_USERNAME` and `DOCKERHUB_TOKEN`; never put them in `.env`, Compose, or Helm values.

## Project layout

```text
apps/agricore-console/      React/Vite console and Nginx edge
services/                   Spring Boot applications
libs/                       Shared API, security, and farm-access libraries
contracts/                  OpenAPI and AsyncAPI contracts
infrastructure/docker/      Database initialization and local assets
infrastructure/helm/        Application Helm chart
infrastructure/monitoring/  Tempo, Prometheus, Loki, Alloy, and Grafana configuration
docs/                       Architecture, ADRs, evidence, and runbooks
scripts/                    Local setup, verification, and seed tools
```

## Security notes

- Passwords use BCrypt; production cost is 12.
- Access tokens are short-lived RS256 JWTs.
- Refresh tokens are opaque, hashed, rotated, and family-revoked on reuse.
- Login rate limiting uses Redis and fails closed in the local stack.
- Never commit `.env`, private keys, tokens, provider credentials, or production secrets.

## License

[MIT](LICENSE)
