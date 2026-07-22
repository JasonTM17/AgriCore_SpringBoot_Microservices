# AgriCore Implementation Plan

**Status:** In progress; assistant and observability delivered, event architecture hardening remains before release
**Created:** 2026-07-16
**Last updated:** 2026-07-22

## Delivered scope

The repository contains 13 Spring applications, a React/Vite console, OpenAPI and AsyncAPI contracts, local Compose stacks, an application Helm chart, observability configuration, security workflows, runbooks, and platform verification scripts.

## Technology stack

- Java 21, Spring Boot 3.5.12, Spring Cloud 2025.0.0, Jackson BOM 2.21.4.
- PostgreSQL 16 with database-per-service and Flyway; Redis 7.
- Apache Kafka 3.8.1 and Kafka UI.
- React 19, Vite 8, TypeScript 5.9, pnpm 11.
- Docker Compose for local operation; Kubernetes/Helm for application workloads.
- JUnit 5, Mockito, AssertJ, Testcontainers, ArchUnit, Vitest, and Playwright.
- Micrometer/OpenTelemetry over OTLP/HTTP to Tempo; Prometheus; Grafana; ECS JSON stdout.

Jaeger, Loki, centralized log aggregation, and MinIO are not part of the delivered stack.

## Application map

| Application | Port | Database | Responsibility |
|---|---:|---|---|
| API gateway | 8080 | — | Routing, JWT validation, external boundary |
| Identity | 8081 | `agricore_identity` | Auth, users, roles, tokens, JWKS |
| Farm | 8082 | `agricore_farm` | Farms, memberships, areas, plots |
| Crop catalog | 8083 | `agricore_crop_catalog` | Crops, varieties, care specifications |
| Crop cycle | 8084 | `agricore_crop_cycle` | Seasons, stages, lifecycle |
| Work | 8085 | `agricore_work` | Field tasks and assignments |
| Inventory | 8086 | `agricore_inventory` | Stock, reservations, movements |
| Harvest | 8087 | `agricore_harvest` | Harvest batches, completion outbox, repair |
| Notification | 8089 | `agricore_notification` | Notification delivery log |
| IoT | 8090 | `agricore_iot` | Devices, readings, threshold alerts |
| Sales | 8091 | `agricore_sales` | Orders and inventory saga orchestration |
| Traceability | 8092 | `agricore_traceability` | QR and public timeline read model |
| Assistant | 8093 | `agricore_assistant` | Persisted assistant generations and bounded farm context |
| React console | 3000 host | — | Same-origin browser experience and Nginx edge |

## Milestones

### M0 — Foundation

Monorepo POM, environment templates, Docker infrastructure, shared libraries, scripts, and ADR process.

### M1 — Identity

Registration, login, refresh rotation, logout, RBAC, account lockout, rate limiting, and JWKS.

### M2 — Farm and catalog

Farm membership boundaries, farms/plots, and seeded crop catalog.

### M3 — Crop cycle and work

Lifecycle transitions, task workflows, and outbox-backed event publication.

### M4 — Harvest and inventory

Harvest completion, stock-in projection, optimistic concurrency, idempotent consumption, and outbox repair controls.

### M5 — Traceability

Idempotent public QR read model without internal identifiers or personal data.

### M6 — IoT, sales, and notification

Sensor ingestion and alert cooldown, inventory-backed sales saga, and notification sink.

### M7 — Gateway and observability

- Gateway routing, RS256/JWKS validation, and caller-token propagation.
- Micrometer OpenTelemetry bridge and OTLP exporter in all 13 Spring applications.
- Local OTLP/HTTP export to Tempo at `http://tempo:4318/v1/traces`, sampling probability `1.0`.
- Prometheus scrape jobs for all 13 applications.
- Prometheus and Tempo Grafana datasources with seven provisioned read-only dashboards.
- ECS JSON stdout plus custom outbox, DLT recovery, harvest, inventory, IoT, sales, and assistant metrics.
- Helm trace endpoint opt-in with sampling default `0.1`; observability backends remain operator-provided.

### M8 — Production hardening

Application Helm chart, security review, runbooks, seed scripts, gateway happy path, Gitleaks, CodeQL, Trivy, Compose validation, and gated Docker publishing.

### M9 — Console and assistant

- React/Vite console with same-origin Nginx edge, generated API clients, browser tests, authenticated assistant chat, citations, refusal, and limited outcomes.
- Assistant PostgreSQL persistence, replayable SSE, idempotency, redacted read-only tool evidence, provider-absence behavior, output screening, and Redis request/token budgets.
- Compose and Helm integration, assistant database provisioning, container hardening, and release gates.

## Remaining pre-release work

- Publish a versioned payload schema and AsyncAPI message for every emitted Kafka event.
- Reject malformed or wrong-version `HarvestCompleted.v1` envelopes to DLT instead of acknowledging them silently.
- Bring farm, crop-cycle, and work publishers to the harvest publisher's locking, bounded-send, index, metric, and test standard.
- Implement the remaining specified domain-event producers and notification consumption as atomic business-change, outbox, contract, and test slices.

## Release acceptance criteria

- Maven `verify` passes for the complete reactor.
- Generated frontend contracts have no drift; lint, typecheck, unit tests, build, and Playwright journeys pass.
- Compose configuration validates; Helm chart lints and renders.
- Gitleaks, CodeQL, and Trivy workflows remain enforced.
- Gateway JWT happy path and Kafka-backed harvest projection are reproducible through the verification scripts.
- Every specified event producer has a transactional outbox path, versioned schema, AsyncAPI message, and focused contract test.
- Kafka consumers validate the exact event type/version and route invalid envelopes through the documented DLT policy.
- Docker images publish only from an eligible verified default-branch revision.
- Production operators supply secrets, infrastructure dependencies, ingress/TLS policy, Kafka authorization, and observability backends before deployment.
