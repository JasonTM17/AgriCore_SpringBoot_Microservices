# AgriCore Implementation Plan

**Status:** Implementation complete; verified default-branch closeout recorded,
without a SemVer release or production deployment claim
**Created:** 2026-07-16
**Last updated:** 2026-07-28

## Delivered scope

The repository contains 13 Spring applications, a React/Vite console, OpenAPI and AsyncAPI contracts, local Compose stacks, an application Helm chart, observability configuration, security workflows, runbooks, and platform verification scripts.

## Technology stack

- Java 21, Spring Boot 3.5.16, Spring Cloud 2025.0.2, Jackson BOM 2.21.4.
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
| Crop cycle | 8084 | `agricore_crop_cycle` | Seasons, stages, lifecycle, overlap exclusion |
| Work | 8085 | `agricore_work` | Field tasks and assignments |
| Inventory | 8086 | `agricore_inventory` | Stock, expiry-aware batches, paged movements, reservations |
| Harvest | 8087 | `agricore_harvest` | Farm-scoped harvest batches, lifecycle invariants, completion outbox, repair |
| Notification | 8089 | `agricore_notification` | Email/in-app requested/sent/failed lifecycle and administrative inbox |
| IoT | 8090 | `agricore_iot` | Devices, readings, threshold alerts |
| Sales | 8091 | `agricore_sales` | Farm-scoped orders and inventory saga orchestration |
| Traceability | 8092 | `agricore_traceability` | QR and public timeline read model with authoritative harvest facts |
| Assistant | 8093 | `agricore_assistant` | Persisted assistant generations and bounded farm context |
| React console | 3000 host | — | Same-origin browser experience and Nginx edge |

## Milestones

### M0 — Foundation

Monorepo POM, environment templates, Docker infrastructure, shared libraries, scripts, and ADR process.

### M1 — Identity

Registration, login, refresh rotation, logout, RBAC, account lockout, rate
limiting, and JWKS. Registration writes `UserRegistered.v1` through the
transactional outbox for Notification's welcome-email path. Identity also owns
a unique permission catalog and role grants, provides `SYSTEM_ADMIN` management
APIs, adds the sorted effective permission snapshot to access JWTs, and maps
role and permission claims consistently at Identity, the gateway, and servlet
domain services.

### M2 — Farm and catalog

Farm membership boundaries, farms/plots, and seeded crop catalog.

### M3 — Crop cycle and work

Lifecycle transitions, task workflows, outbox-backed event publication, and a
PostgreSQL exclusion constraint that prevents concurrent overlapping
`DRAFT`/`ACTIVE` cycles for one plot.

### M4 — Harvest and inventory

Farm-scoped Harvest completion/events, positive weight/status constraints,
farm-scoped Inventory reservations, stock-in projection, optimistic
concurrency, expiry-aware batch allocation, paged ledger queries, idempotent
consumption, and outbox repair controls. Additive scope migrations retain
nullable legacy rows; runtime paths fail closed or re-authorize through
authoritative stored relationships.

### M5 — Traceability

Idempotent public QR read model without internal identifiers or personal data; product code and gross weight are projected only when supplied by the authoritative harvest event.

### M6 — IoT, sales, and notification (delivered)

HTTP and QoS 1 MQTT sensor ingestion with stable reading-ID deduplication,
per-device token-bucket/in-flight admission, and alert cooldown; farm-scoped,
Inventory-backed Sales saga with price snapshots, order-item persistence,
durable reservation reconciliation, bounded recovery leases/backoff, fulfillment
milestones, and transactional order lifecycle events; and idempotent
Notification consumption of Identity, Sales, Traceability, and IoT events.
External notification delivery is at-most-once automatically: an ambiguous
stale provider attempt ends as `FAILED`/`DELIVERY_OUTCOME_UNKNOWN` instead of
being resent. In-app notifications persist locally, are safely retryable, and
are available through authorized administrative list/mark-read endpoints.
Contract-invalid Notification records bypass retries and reach the DLT without
side effects. Current lifecycle outbox events use the v2 schemas retained
alongside immutable historical v1 schemas.

### M7 — Gateway and observability

- Gateway routing, RS256/JWKS validation, and caller-token propagation.
- Micrometer OpenTelemetry bridge and OTLP exporter in all 13 Spring applications.
- Local OTLP/HTTP export to Tempo at `http://tempo:4318/v1/traces`, sampling probability `1.0`.
- Prometheus scrape jobs for all 13 applications, with the internal gateway and
  assistant on the Compose network.
- Prometheus, Tempo, and Loki Grafana datasources with seven provisioned read-only dashboards.
- ECS JSON stdout collected by Alloy into Loki with 72-hour local retention and bounded Docker log files, plus custom outbox, DLT recovery, harvest, inventory, IoT, sales, assistant generation, and assistant retention metrics.
- MinIO local object storage with loopback-only API and console bindings.
- Helm trace endpoint opt-in with sampling default `0.1`; observability and object-storage backends remain operator-provided in clusters.

### M8 — Production hardening

Application Helm chart with read-only application filesystems, bounded `/tmp`,
portable gateway Service alias, configurable external egress, and Farm/Crop-cycle/
Inventory dependency wiring; security review; runbooks; bounded cross-service
seed profiles; gateway happy path; Gitleaks; CodeQL; filesystem/built-image
Trivy; Compose runtime-contract validation; and digest-gated dual-registry
promotion that can promote only full/short SHA tags. All 14 Dockerfiles pin
their build and runtime bases by digest and receive `GIT_SHA` as the OCI revision
label; this is configuration, not registry-publication evidence.

### M9 — Console and assistant

- React/Vite console with same-origin Nginx edge, generated API clients, browser tests, authenticated assistant chat, citations, refusal, and limited outcomes.
- Session-epoch protection serializes logout/replacement login and rejects stale
  refresh results; mobile navigation mounts only while open.
- Dashboard and public traceability use repository-owned 240/480/960 pixel WebP
  variants, route-specific responsive selection, lazy/eager priorities, fixed
  geometry, and accessible broken-image fallbacks.
- Assistant PostgreSQL persistence, replayable SSE, idempotency, redacted read-only tool evidence, provider-absence behavior, output screening, and Redis request/token budgets.
- Compose and Helm integration, assistant database provisioning, container hardening, and release gates.
- Gateway authenticates the canonical client-IP value for Identity rate limits
  and Assistant budgets with a shared HMAC signing secret. It accepts forwarded
  input only from the configured immediate trusted proxy; services otherwise
  fall back to their remote peer.

## Closeout and operator handoff

The completed default-branch verification, merge, immutable package publication,
and registry digest parity are recorded in the
[release closeout](evidence/release-closeout-2026-07-28.md). The earlier
[2026-07-26 verification](evidence/release-verification-2026-07-26.md) remains
a qualified historical runtime snapshot for `5867b37` only.

Before a production deployment, operators must re-check environment-owned JWT,
database, Kafka, SMTP, provider, TLS, ACL, backup, and observability inputs.
Those are deployment responsibilities, not incomplete repository features.

## Release acceptance criteria

- Maven `verify` passes for the complete reactor.
- PostgreSQL Testcontainers migration tests verify the durable outbox retry
  schema, partial indexes, and non-blocking `SKIP LOCKED` claims with Docker
  available.
- Generated frontend contracts have no drift; lint, typecheck, unit tests, build, and Playwright journeys pass.
- Compose configuration validates; Helm chart lints and renders.
- Gitleaks, CodeQL, and Trivy workflows remain enforced.
- Gateway and service JWT negative-path checks, the authenticated happy path, duplicate projection, notification dedupe, and Harvest DLT routing are reproducible through the verification scripts.
- Bounded `Smoke`/`Quick`, `Demo`/`Showcase`, and `Large` seed profiles are
  repeatable, avoid cross-service database writes except the documented local
  bootstrap, wait for event projections, and upload repository-owned
  checksummed media through the Work attachment API.
- Every implemented event producer has a transactional outbox path, versioned schema, AsyncAPI message, and focused contract test.
- Kafka consumers validate the exact event type/version and route invalid envelopes through the documented DLT policy.
- Docker images publish only full/short SHA tags from an eligible verified
  default-branch revision; `latest` is never promoted.
- Production operators supply secrets, infrastructure dependencies, ingress/TLS policy, Kafka authorization, and observability backends before deployment.
