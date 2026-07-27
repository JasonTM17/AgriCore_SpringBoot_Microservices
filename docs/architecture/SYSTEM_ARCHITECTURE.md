# AgriCore System Architecture

**Last updated:** 2026-07-27
**Status:** Active

## 1. Purpose

AgriCore manages the agricultural production chain from farms and crop cycles through field work, harvest, inventory, sales, and QR traceability. IoT monitoring, notifications, and a bounded assistant support those workflows.

## 2. Architectural style

- Microservices split by business capability.
- Database per service; no cross-service SQL joins.
- Hexagonal/Clean Architecture within services.
- REST through a single API gateway for synchronous operations.
- Kafka, transactional outbox, and idempotent consumers for implemented asynchronous flows.
- RS256 JWT and JWKS validation at the gateway and domain services.

## 3. System context

```text
[Browser / API client]
          |
  [Console + Nginx :3000]
          | /api, /public/api
  [API Gateway :8080]
          |
  +-------+--------+--------------------+
  |       |        |                    |
Identity Farm   Domain services     Assistant
  |       |        |                    |
  +-------+--- Kafka -------------------+
              |
       Inventory + Traceability

[PostgreSQL databases] [Redis] [Kafka]
[Prometheus] [Tempo] [Grafana]
```

The browser uses a same-origin edge: Nginx serves `/` and forwards `/api` and `/public/api` to the gateway. The assistant service is an internal gateway target and is not host-published by Compose.

## 4. Application boundaries

| Application | Owns or performs | Runtime events |
|---|---|---|
| API gateway | Routing, JWT validation, external boundary | None |
| Identity | Users, roles, permission catalog and role grants, access and refresh tokens, JWKS | Publishes `UserRegistered.v1` through its transactional outbox |
| Farm | Farms, areas, plots, memberships | Publishes farm events through outbox |
| Crop catalog | Crops, varieties, care specifications | None |
| Crop cycle | Cycles, stages, lifecycle, PostgreSQL overlap exclusion | Publishes crop-cycle events through outbox |
| Work | Tasks, assignments, and private task attachments | Publishes work events through outbox; attachments use the bounded MinIO-compatible storage adapter |
| Harvest | Farm-scoped harvest batches and completion repair | Verifies Farm and Crop-cycle scope; publishes farm-scoped `HarvestCompleted.v1` through outbox |
| Inventory | Stock, expiry-aware lots, farm-scoped reservations, movements | Consumes farm-scoped `HarvestCompleted.v1`, idempotent with DLT recovery; publishes stock events |
| Traceability | Public QR timeline/read model | Consumes `HarvestCompleted.v1`, idempotent with DLT recovery; publishes QR lifecycle events |
| IoT | Devices, readings, threshold alerts, per-device MQTT admission | Publishes reading, threshold, and offline events through outbox |
| Sales | Farm-scoped customers, orders, order items, and inventory saga state | Verifies Farm scope; bounded farm-scoped reserve/confirm attempt plus durable retry/timeout recovery; publishes order lifecycle events |
| Notification | Truthful email/in-app delivery lifecycle, administrative inbox, and event dedupe | Consumes Identity, Sales, Traceability, and IoT events; invalid payloads bypass retries; publishes requested, sent, and failed v2 events |
| Assistant | Conversations, messages, generations, event replay, authorized farm facts, curated knowledge retrieval | No Kafka event path implemented; retrieval stays inside its database |

Implemented consumer topology includes `HarvestCompleted.v1` from Harvest to
Inventory and Traceability. Notification consumes Identity
`UserRegistered.v1`, Sales order, Traceability QR, and IoT alert/offline events.
The registration event carries the bounded fields needed for a durable welcome
email and excludes credential material. Consumers persist a stable event marker
with each side effect. Transient Kafka failures use bounded retry topics;
contract failures raise `IllegalArgumentException` and route directly to
`<topic>.DLT` without retry churn or persisted side effects. The repository E2E
script also republishes a harvest event, publishes a duplicate notification
event, and injects a wrong-version harvest event to prove idempotency and DLT
routing on a real broker.

### Durable outbox retry state

Publisher retry state is distinct from Kafka consumer retry/DLT state. Farm,
Harvest, Identity, Notification, Sales, Traceability, and Work keep nullable
`next_attempt_at` and `quarantined_at` fields so legacy null rows remain due.
Their PostgreSQL migrations create retry/quarantine partial indexes
concurrently outside a Flyway transaction. Testcontainers migration coverage
checks schema types, indexes, due/deferred/quarantined selection, and
non-blocking `FOR UPDATE SKIP LOCKED` claims; it requires Docker.

### Farm-scope upgrade boundary

- Harvest V6 and Sales V6 add nullable `farm_id` columns so older databases can
  migrate without fabricating ownership. New records always persist
  authoritative farm scope.
- Harvest derives scope from the stored plot and verifies any non-null stored
  farm before reads/repair. Its completion event requires `farmId`.
- Sales requires stored farm scope for reads and recovery, and passes it to
  Inventory reserve, business-reference lookup, confirm, and release calls.
- Inventory checks that the requested farm owns the reservation's item
  warehouse. Legacy warehouses or processed markers without scope fail closed
  until an operator maps them.

### Assistant boundary

- Own database: `agricore_assistant`.
- Authenticated generation metadata and fetch-SSE replay through the gateway.
- Provider `none` by default; provider secrets are deployment inputs only.
- Current tool access is authenticated, read-only, host-allowlisted farm data with row, byte, and timeout bounds.
- Optional RAG uses versioned knowledge chunks and an indexed term table in
  `agricore_assistant`; top-k `KB-*` citations are merged into the persisted
  evidence snapshot with prepared SQL and existing input/output budgets.
- Redis-backed request/token budgets fail closed when Redis is unavailable.
- Expiry timestamps and a bounded cleanup job govern archived conversations,
  audit events, and generation replay events; defaults are 90 days, 365 days,
  and 24 hours respectively.
- Autonomous writes, arbitrary URLs, user-controlled RAG ingestion, and
  cross-service database access are out of scope.

### Notification delivery boundary

- A notification is persisted as `REQUESTED` before delivery and ends as
  `SENT` or `FAILED`.
- External channels use at-most-once automatic delivery. A recoverable adapter
  result is recorded rather than automatically retried, and a stale
  `DELIVERING` row becomes `FAILED` with `DELIVERY_OUTCOME_UNKNOWN` because the
  provider may already have accepted the message.
- `IN_APP` delivery is a local idempotent write keyed by notification ID, so its
  stale lease can be reclaimed within the bounded retry budget.
- Kafka source-event idempotency prevents a replay from creating a second
  notification intent; it cannot make an external SMTP provider exactly once.

### Inventory batch allocation

Inventory keeps the aggregate item balance for fast reads and an `inventory_batches`
ledger for lot-level correctness. Stock-in creates or increments a lot, while
reservation and dispatch allocation lock the item's batches and consume eligible
lots in FEFO order (earliest expiry first, then receipt time). Expired batches are
never newly reserved or dispatched. The V6 migration backfills one non-expiring
legacy lot per existing item and preserves reservation balances through allocation
rows, so the aggregate and lot ledger can be reconciled after deployment.

### Crop-cycle overlap invariant

Application checks provide a friendly `409 CROP_CYCLE_OVERLAP`, while
PostgreSQL remains authoritative under concurrency. Migration V5 installs
`btree_gist` and excludes intersecting inclusive planned date ranges for the
same plot when either row is `DRAFT` or `ACTIVE`. Terminal rows do not block
plot reuse. Existing overlaps must be resolved before applying the migration.

### Console session and media boundaries

- A session epoch prevents a late refresh from restoring a logged-out or
  replaced session. New login waits for any active logout request to settle.
- Desktop and mobile navigation are separate render paths; the mobile drawer is
  absent from the DOM while closed.
- Showcase images use fixed aspect-ratio frames, deterministic accessible
  fallbacks, and 240/480/960 pixel WebP `srcset` variants with route-specific
  `sizes` and loading priority.

### IoT time-series persistence

IoT readings are stored in PostgreSQL/TimescaleDB. The service keeps the
idempotency ledger and reading table in the same transaction, then converts
`sensor_readings` to a seven-day hypertable with a composite
`(id, recorded_at)` primary key. The Compose and Helm paths require the
Timescale extension/preflight before IoT starts; upgrades use a controlled
recreate so old writers cannot race a schema conversion.

Seven days is the hypertable chunk interval, not a deletion horizon. The
repository deliberately installs no Timescale retention policy because raw and
aggregate telemetry retention remains an operator/product decision.

## 5. Communication patterns

| Need | Pattern |
|---|---|
| External and synchronous service operations | REST through the gateway |
| Cross-service farm authorization | Authenticated REST to farm-service with the caller bearer token |
| Implemented domain event | Kafka |
| Atomic database change and event publication | Transactional outbox |
| Cross-service order transaction | Sales orchestration saga with bounded request latency, persistent recovery lease/backoff, compensation, and manual-reconciliation timeout |
| Public aggregated view | Traceability local read model |

## 6. Internal layers

```text
api → application → domain ← infrastructure
```

- Controllers handle HTTP and validation, not business rules.
- Application services coordinate use cases and transactions.
- Domain code contains models, policies, and domain exceptions.
- Infrastructure contains JPA, Kafka, Redis, security, and downstream adapters.

## 7. Security architecture

- Gateway and servlet domain services validate RS256 JWTs against identity-service JWKS, issuer, and `agricore-api` audience.
- Identity resolves the sorted distinct union of permissions granted through a user's roles when it issues an access token. The resulting `roles` and `permissions` claims are token-lifetime snapshots.
- Identity, the API gateway, and servlet domain services map string role entries to `ROLE_*` and string permission entries to `PERMISSION_*`. Malformed or blank entries are ignored and authorities are deduplicated.
- Permission catalog reads and role-grant mutation use canonical identity permissions; mutation still requires the `SYSTEM_ADMIN` role as a second administrative boundary. Role grant replacement uses a pessimistic role lock and validates all requested codes before changing the grant set.
- Canonical `PERMISSION_*` authorities are enforced at the Identity, Work, Harvest, Inventory, Sales, Traceability, IoT, Assistant, and Notification controller boundaries. The React console filters navigation by the effective permission snapshot as well as role metadata. Farm membership and internal service-token checks remain separate scope boundaries.
- Gateway routes preserve the caller bearer token. `libs/farm-access-client`
  forwards it from Crop-cycle, Work, Harvest, Inventory, IoT, and Sales to
  Farm.
- `farm_memberships` maps JWT subjects to farm scope. `ROLE_SYSTEM_ADMIN` is the explicit global override.
- Plot resolution masks missing, inaccessible, and mismatched plots as `404`.
- Farm-access network errors, unexpected statuses, invalid responses, and missing request authentication fail closed as `503 FARM_ACCESS_UNAVAILABLE`.
- Dev identity headers are accepted only when `agricore.security.dev-mode=true`; Compose and Helm set dev mode off.
- Gateway and servlet domain services do not establish browser sessions. Unsafe ambient-cookie
  mutations without explicit header credentials fail closed through non-persisting CSRF
  repositories. Gateway exempts only `/api/v1/auth/**` from this edge matcher because Identity's
  browser endpoints independently require an exact allowed `Origin`/`Referer` before consuming
  their path-scoped refresh cookie.
- Gateway removes untrusted forwarding headers, accepts `X-Forwarded-For` only
  from an immediate peer matching its trusted-proxy pattern, and HMAC-signs an
  audience-bound canonical client-IP payload only for the `identity-service`
  and `assistant-service` route IDs. Identity verifies the `identity-service`
  audience and Assistant verifies the `assistant-service` audience; missing,
  malformed, invalid, or cross-audience header pairs fall back to the direct
  peer. The shared signing secret is mounted only by Gateway, Identity, and
  Assistant; neither consumer has direct public ingress.
- Passwords use BCrypt. Refresh tokens are opaque, hashed, rotated, and revocable.
- Secrets come from environment variables or Kubernetes Secrets, not committed configuration.

```text
Identity permissions + role_permissions
  -> access JWT roles[] + permissions[] snapshot
  -> Identity / API Gateway / servlet service authority conversion
  -> ROLE_* + PERMISSION_* in the Spring Security context
```

Grant changes appear only in newly issued access tokens, such as after login or refresh; existing tokens retain their previous snapshot until expiry. The default access-token TTL is 900 seconds. Dev-only header authentication derives the same catalog snapshot when an explicit permission header is absent, while an explicitly supplied header is treated as authoritative for negative-path testing.

See [Microservices authorization model](../security/microservices-authz.md) for endpoint and failure semantics.

## 8. Observability architecture

All 13 Spring applications include `micrometer-tracing-bridge-otel`, `opentelemetry-exporter-otlp`, and `micrometer-registry-prometheus`. They expose Actuator health and `/actuator/prometheus`.

```text
Spring observations → Micrometer OTel bridge → OTLP/HTTP → Tempo
/actuator/prometheus ← Prometheus ← Grafana
Tempo traces ────────────────────────────────↑
ECS JSON logs → container stdout → Alloy → Loki → Grafana
```

### Environment behavior

| Environment | Trace endpoint | Sampling | Logging |
|---|---|---:|---|
| Local Compose | `http://tempo:4318/v1/traces` | `1.0` | ECS JSON, Alloy collection into Loki, environment `local` by default |
| Helm | Empty by default; export starts only when configured | `0.1` default when export is enabled | ECS JSON when `observability.structuredLogging=true` |

Prometheus defines 13 scrape jobs: the internal gateway and assistant service
on the shared Compose network, plus 11 development-published applications
through `host.docker.internal`. Grafana provisions non-editable Prometheus,
Tempo, and Loki datasources, Prometheus exemplar links to Tempo, and seven
read-only dashboards:

1. AgriCore Platform Overview
2. AgriCore Service Health
3. AgriCore Database Health
4. AgriCore Kafka Health
5. AgriCore Inventory Operations
6. AgriCore IoT Ingestion
7. AgriCore Business Metrics

### Custom Prometheus metric families

| Family | Meaning |
|---|---|
| `agricore_outbox_backlog` | Unpublished transactional outbox rows across event-producing services |
| `agricore_outbox_pending` | Unpublished, non-quarantined outbox rows, including scheduled backoff |
| `agricore_outbox_quarantined` | Terminal outbox rows awaiting reviewed repair |
| `agricore_kafka_dlq_attempts_total{consumer=...}` | Records handed to DLT recovery; not DLT depth or confirmed publish success |
| `agricore_harvest_processing_seconds_*` | Harvest completion latency histogram and timer series by outcome |
| `agricore_inventory_reservations_total` | Reservation outcomes |
| `agricore_inventory_harvest_events_total` | Applied and duplicate harvest events |
| `agricore_iot_readings_total` | Accepted sensor readings |
| `agricore_iot_alerts_total` | Created and suppressed alert outcomes |
| `agricore_iot_open_alerts` | Current open alert count |
| `agricore_sales_sagas_total` | Sales saga terminal outcomes |
| `agricore_notification_deliveries_total` | Notification delivery outcomes by sent, failed, or duplicate |
| `agricore_assistant_generations_total` | Completed, failed, and cancelled generations |
| `agricore_assistant_retention_purged_total` | Physically deleted generation events, archived conversations, and audit events |
| `agricore_assistant_retention_cleanup_failures_total` | Retention cleanup failures |

Alloy discovers only containers carrying this repository's Compose project labels, drops its own and Loki's containers to avoid recursive ingestion, enriches service labels, and forwards ECS JSON/stdout to Loki. Loki uses local filesystem storage with 72-hour retention and rejects older samples. Docker's json-file logs are bounded to three 10 MiB files per container by default. The Docker socket is mounted read-only for discovery and is therefore a local host trust boundary. Tempo uses non-persistent container storage with 48-hour configured retention in the local stack.

## 9. Deployment scope

| Environment | Repository mechanism | Scope |
|---|---|---|
| Local | `docker-compose.yml` plus `docker-compose.observability.yml` | Infrastructure, Mailpit, MinIO, 13 applications, console, Tempo, Prometheus, Alloy, Loki, Grafana |
| Cluster | `infrastructure/helm/agricore` | 13 application Deployments/Services, console, gateway Service alias, optional Ingress, assistant database Job |
| CI | GitHub Actions | Build/test, frontend, secret, Compose, Helm, CodeQL, filesystem and built-image Trivy, plus configured digest-gated full/short-SHA dual-registry promotion |

All application containers use a read-only root filesystem and a bounded
writable `/tmp` `emptyDir`. Harvest receives Farm and Crop-cycle access
configuration; Sales receives Farm access, Inventory URL, and the internal
Inventory credential. The Console uses the `api-gateway` alias in both Compose
and Kubernetes.

The chart expects external PostgreSQL, Redis, Kafka, MinIO-compatible object
storage, SMTP, and observability services plus pre-created database and SMTP
credential Secrets. NetworkPolicy denies non-AgriCore ingress by default.
Egress is unrestricted unless `networkPolicy.restrictEgress=true`; restricted
deployments must add their external dependency destinations through
`networkPolicy.additionalEgress`. The chart does not install Tempo, Prometheus,
Loki, Alloy, Grafana, or MinIO. These repository mechanisms do not prove a
production cluster is deployed.

All 14 Dockerfiles pin build and runtime base images by digest and apply the
`GIT_SHA` OCI revision label. Candidate parity, scan, signing, and immutable
tag promotion are workflow configuration; they do not prove a registry release.

## 10. Non-goals

- No Eureka or Consul.
- No shared JPA entities across services.
- No Debezium until polling outbox is insufficient.
- No cross-service media ownership is introduced; Work owns private task attachments through its MinIO-compatible adapter, while repository-owned showcase media is static demo content.
- No production log aggregation deployment is claimed; local Compose provides bounded Alloy/Loki collection only.

## References

- [Observability ADR](../adr/0010-observability-stack.md)
- [Local operations](../runbooks/local-operations.md)
- [Security review](../security/SECURITY_REVIEW.md)
- [Microservices authorization model](../security/microservices-authz.md)
- OpenAPI contracts under `contracts/openapi/`
- AsyncAPI contracts under `contracts/asyncapi/`
