# AgriCore Implementation Plan

**Status:** Delivered on feature branch; release/publish gates remain environment-dependent
**Created:** 2026-07-16  
**CK Plan:** `plans/260718-1232-agricore-web-assistant/`

## Repository Scout Summary

| Item | Finding |
|------|---------|
| Git | Initialized; focused conventional commits on `feature/agricore-web-assistant-ck` |
| Application code | Java 21/Spring Boot services plus React/Vite console and assistant service |
| README | Present; documents console, assistant, Compose, Helm, and Docker Hub workflow |
| Existing assets | OpenAPI/AsyncAPI contracts, Docker Compose, Helm, CI, security and runbooks |
| Risk of overwrite | None — pure greenfield |

The original scout rows above describe the pre-implementation snapshot; the repository is now initialized and contains the services, console, assistant, contracts, deployment manifests, and CI gates listed below.

## Technology Stack (locked)

- Java 21 (compile target), Spring Boot 3.4.x, Spring Cloud 2024.0.x
- PostgreSQL 16 (DB per service), Flyway, Redis 7
- Apache Kafka 3.x + Kafka UI
- Docker Compose (dev), Kubernetes/Helm (deploy)
- JUnit 5, Mockito, AssertJ, Testcontainers, ArchUnit
- OpenTelemetry → Tempo/Jaeger, Prometheus, Grafana, Loki
- MinIO (object storage, optional phase)

## Service Map

| Service | Port | DB | Responsibility |
|---------|------|-----|----------------|
| api-gateway | 8080 | — | Routing, JWT validation, CORS |
| identity-service | 8081 | agricore_identity | Auth, users, roles, tokens |
| farm-service | 8082 | agricore_farm | Farms, areas, plots |
| crop-catalog-service | 8083 | agricore_crop_catalog | Crops, varieties, care specs |
| crop-cycle-service | 8084 | agricore_crop_cycle | Seasons, stages, cycle state |
| work-service | 8085 | agricore_work | Field tasks, assignments |
| inventory-service | 8086 | agricore_inventory | Materials, produce stock |
| harvest-service | 8087 | agricore_harvest | Harvest batches, QC |
| traceability-service | 8088 | agricore_traceability | QR, public timeline |
| notification-service | 8089 | agricore_notification | Email/in-app/webhook fan-out |
| iot-service | 8090 | agricore_iot | Sensors, TS readings, alerts |
| sales-service | 8091 | agricore_sales | Orders, saga orchestration |
| assistant-service | 8093 | agricore_assistant | Persisted authenticated assistant generations and bounded read-only farm context |

## Milestones

### M0 — Foundation
Monorepo POM, `.gitignore`, `.env.example`, Docker infra, `common-lib`, scripts, ADRs, git init.

### M1 — Identity
Register, login, refresh rotation, logout, RBAC, account lockout, rate limit, JWKS.

### M2 — Farm & Catalog
CRUD farms/plots, crop catalog seed data (Robusta, Ri6, ST25, etc.).

### M3 — Crop Cycle & Work
Cycle lifecycle, stage transitions, work tasks, Kafka events via outbox.

### M4 — Harvest & Inventory
Harvest complete → stock add; optimistic concurrency; idempotent consumers.

### M5 — Traceability
Event-sourced read model; public QR API; no internal data leak.

### M6 — IoT, Sales, Notification
Sensor ingest + alert cooldown; sales saga with compensation; notifications.

### M7 — Gateway & Observability
Spring Cloud Gateway; Actuator/Prometheus; OTel; CI workflows.

### M8 — Production Hardening
Helm charts, security review, runbooks, seed scripts, e2e happy path.

### M9 — Console and assistant delivery

- React/Vite console with same-origin Nginx edge, authenticated assistant chat, citations, refusal/limited outcomes, and generated API clients.
- Assistant service with PostgreSQL persistence, authenticated replayable SSE, idempotency, redacted read-only tool evidence, provider-absence behavior, deterministic output screening, and Redis request/token budgets.
- Gateway, Compose, Helm, observability, Docker build/publish matrix, security scans, documentation, and small conventional commits.

## Commit Strategy

Conventional Commits, one concern per commit, tests green before commit:

```text
chore(repo): initialize monorepo structure
feat(common): add event envelope and error model
feat(identity): implement registration and login
...
```

## Definition of Done (per task)

Build + tests + validation + authz + Flyway + OpenAPI/event schema + no secrets + docs + commit.

## CK Plan Link

Detailed phase files: `plans/260716-2028-agricore-platform/phase-*.md`
