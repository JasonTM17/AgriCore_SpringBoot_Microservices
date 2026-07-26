# AgriCore Implementation Plan

**Status:** Implementation and local release verification complete; external registry publication remains
**Created:** 2026-07-16
**Last updated:** 2026-07-26

## Delivered scope

The repository contains 13 Spring applications, a React/Vite console, OpenAPI and AsyncAPI contracts, local Compose stacks, an application Helm chart, observability configuration, security workflows, runbooks, and platform verification scripts.

## Technology stack

- Java 21, Spring Boot 3.5.12, Spring Cloud 2025.0.0, Jackson BOM 2.21.4.
- PostgreSQL 16 with database-per-service and Flyway; Redis 7.
- Apache Kafka 3.8.1 and Kafka UI.
- React 19, Vite 8, TypeScript 5.9, pnpm 11.
- Docker Compose for local operation; Kubernetes/Helm for application workloads.
- JUnit 5, Mockito, AssertJ, Testcontainers, ArchUnit, Vitest, and Playwright.
- Micrometer/OpenTelemetry over OTLP/HTTP to Tempo; Prometheus; Grafana; Alloy/Loki log collection; ECS JSON stdout; MinIO local object storage.

Jaeger is not part of the delivered stack. Production object storage and log retention remain operator-provided; local Compose provisions MinIO and a bounded Alloy/Loki stack for realistic development evidence.

## Application map

| Application | Port | Database | Responsibility |
|---|---:|---|---|
| API gateway | 8080 | — | Routing, JWT validation, external boundary |
| Identity | 8081 | `agricore_identity` | Auth, users, roles, tokens, JWKS |
| Farm | 8082 | `agricore_farm` | Farms, memberships, areas, plots |
| Crop catalog | 8083 | `agricore_crop_catalog` | Crops, varieties, care specifications |
| Crop cycle | 8084 | `agricore_crop_cycle` | Seasons, stages, lifecycle |
| Work | 8085 | `agricore_work` | Field tasks and assignments |
| Inventory | 8086 | `agricore_inventory` | Stock, expiry-aware batches, paged movements, reservations |
| Harvest | 8087 | `agricore_harvest` | Harvest batches, lifecycle invariants, completion outbox, repair |
| Notification | 8089 | `agricore_notification` | Requested/sent/failed notification delivery lifecycle |
| IoT | 8090 | `agricore_iot` | Devices, readings, threshold alerts |
| Sales | 8091 | `agricore_sales` | Orders and inventory saga orchestration |
| Traceability | 8092 | `agricore_traceability` | QR and public timeline read model with authoritative harvest facts |
| Assistant | 8093 | `agricore_assistant` | Persisted assistant generations and bounded farm context |
| React console | 3000 host | — | Same-origin browser experience and Nginx edge |

## Milestones

### M0 — Foundation

Monorepo POM, environment templates, Docker infrastructure, shared libraries, scripts, and ADR process.

### M1 — Identity

Registration, login, refresh rotation, logout, RBAC, account lockout, rate limiting, and JWKS. Identity also owns a unique permission catalog and role grants, provides `SYSTEM_ADMIN` management APIs, adds the sorted effective permission snapshot to access JWTs, and maps role and permission claims consistently at Identity, the gateway, and servlet domain services.

### M2 — Farm and catalog

Farm membership boundaries, farms/plots, and seeded crop catalog.

### M3 — Crop cycle and work

Lifecycle transitions, task workflows, and outbox-backed event publication.

### M4 — Harvest and inventory

Harvest completion, positive weight/status constraints, stock-in projection, optimistic concurrency, expiry-aware batch allocation, paged ledger queries, idempotent consumption, and outbox repair controls.

### M5 — Traceability

Idempotent public QR read model without internal identifiers or personal data; product code and gross weight are projected only when supplied by the authoritative harvest event.

### M6 — IoT, sales, and notification (delivered)

HTTP and QoS 1 MQTT sensor ingestion with stable reading-ID deduplication and alert cooldown, inventory-backed sales saga with price snapshots, order-item persistence, durable reservation reconciliation, bounded recovery leases/backoff, fulfillment milestones, transactional order lifecycle events, and idempotent Notification consumption of Sales, Traceability, and IoT events. Notification email uses bounded SMTP delivery, while in-app notifications persist locally; both produce truthful lifecycle events.

### M7 — Gateway and observability

- Gateway routing, RS256/JWKS validation, and caller-token propagation.
- Micrometer OpenTelemetry bridge and OTLP exporter in all 13 Spring applications.
- Local OTLP/HTTP export to Tempo at `http://tempo:4318/v1/traces`, sampling probability `1.0`.
- Prometheus scrape jobs for all 13 applications.
- Prometheus, Tempo, and Loki Grafana datasources with seven provisioned read-only dashboards.
- ECS JSON stdout collected by Alloy into Loki with 72-hour local retention and bounded Docker log files, plus custom outbox, DLT recovery, harvest, inventory, IoT, sales, and assistant metrics.
- MinIO local object storage with loopback-only API and console bindings.
- Helm trace endpoint opt-in with sampling default `0.1`; observability and object-storage backends remain operator-provided in clusters.

### M8 — Production hardening

Application Helm chart, security review, runbooks, seed scripts, gateway happy path, Gitleaks, CodeQL, Trivy, Compose validation, and gated Docker publishing.

### M9 — Console and assistant

- React/Vite console with same-origin Nginx edge, generated API clients, browser tests, authenticated assistant chat, citations, refusal, and limited outcomes.
- Assistant PostgreSQL persistence, replayable SSE, idempotency, redacted read-only tool evidence, provider-absence behavior, output screening, and Redis request/token budgets.
- Compose and Helm integration, assistant database provisioning, container hardening, and release gates.

## Remaining pre-release work

- Push the verified default-branch revision to Docker Hub/GHCR through the gated workflow and publish GitHub package metadata.
- Re-check production operator inputs (JWT keys, database/Kafka/SMTP credentials, TLS, ACLs, observability backends) before deployment.

Local Compose, JVM, frontend, browser, event-resilience, media, seed, and trace
checks are recorded in [release verification 2026-07-26](evidence/release-verification-2026-07-26.md).

## Release acceptance criteria

- Maven `verify` passes for the complete reactor.
- Generated frontend contracts have no drift; lint, typecheck, unit tests, build, and Playwright journeys pass.
- Compose configuration validates; Helm chart lints and renders.
- Gitleaks, CodeQL, and Trivy workflows remain enforced.
- Gateway and service JWT negative-path checks, the authenticated happy path, duplicate projection, notification dedupe, and Harvest DLT routing are reproducible through the verification scripts.
- Bounded seed profiles are repeatable, avoid cross-service database writes except the documented local bootstrap, and include repository-owned media checksums.
- Every implemented event producer has a transactional outbox path, versioned schema, AsyncAPI message, and focused contract test.
- Kafka consumers validate the exact event type/version and route invalid envelopes through the documented DLT policy.
- Docker images publish only from an eligible verified default-branch revision.
- Production operators supply secrets, infrastructure dependencies, ingress/TLS policy, Kafka authorization, and observability backends before deployment.
