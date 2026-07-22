# AgriCore System Architecture

**Last updated:** 2026-07-22
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
| Identity | Users, roles, access and refresh tokens, JWKS | None; outbox table is unused |
| Farm | Farms, areas, plots, memberships | Publishes farm events through outbox |
| Crop catalog | Crops, varieties, care specifications | None |
| Crop cycle | Cycles, stages, lifecycle | Publishes crop-cycle events through outbox |
| Work | Tasks and assignments | Publishes work events through outbox |
| Harvest | Harvest batches and completion repair | Publishes `HarvestCompleted.v1` through outbox |
| Inventory | Stock, reservations, movements | Consumes `HarvestCompleted.v1`, idempotent with DLT recovery |
| Traceability | Public QR timeline/read model | Consumes `HarvestCompleted.v1`, idempotent with DLT recovery |
| IoT | Devices, readings, threshold alerts | No Kafka event path implemented |
| Sales | Orders and inventory saga state | Synchronous reserve/confirm/release calls to inventory |
| Notification | Delivery log | No Kafka event path implemented |
| Assistant | Conversations, messages, generations, event replay, redacted tool evidence | No Kafka event path implemented |

The only implemented consumer topology is `HarvestCompleted.v1` from harvest to inventory and traceability. Farm, crop-cycle, and work publish events, but no broader event mesh is claimed.

### Assistant boundary

- Own database: `agricore_assistant`.
- Authenticated generation metadata and fetch-SSE replay through the gateway.
- Provider `none` by default; provider secrets are deployment inputs only.
- Current tool access is authenticated, read-only, host-allowlisted farm data with row, byte, and timeout bounds.
- Redis-backed request/token budgets fail closed when Redis is unavailable.
- Autonomous writes, arbitrary URLs, RAG ingestion, and cross-service database access are out of scope.

## 5. Communication patterns

| Need | Pattern |
|---|---|
| External and synchronous service operations | REST through the gateway |
| Cross-service farm authorization | Authenticated REST to farm-service with the caller bearer token |
| Implemented domain event | Kafka |
| Atomic database change and event publication | Transactional outbox |
| Cross-service order transaction | Sales orchestration saga with inventory compensation |
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
- Gateway routes preserve the caller bearer token. `libs/farm-access-client` forwards it from crop-cycle, work, harvest, and IoT to farm-service.
- `farm_memberships` maps JWT subjects to farm scope. `ROLE_SYSTEM_ADMIN` is the explicit global override.
- Plot resolution masks missing, inaccessible, and mismatched plots as `404`.
- Farm-access network errors, unexpected statuses, invalid responses, and missing request authentication fail closed as `503 FARM_ACCESS_UNAVAILABLE`.
- Dev identity headers are accepted only when `agricore.security.dev-mode=true`; Compose and Helm set dev mode off.
- Passwords use BCrypt. Refresh tokens are opaque, hashed, rotated, and revocable.
- Secrets come from environment variables or Kubernetes Secrets, not committed configuration.

See [Microservices authorization model](../security/microservices-authz.md) for endpoint and failure semantics.

## 8. Observability architecture

All 13 Spring applications include `micrometer-tracing-bridge-otel`, `opentelemetry-exporter-otlp`, and `micrometer-registry-prometheus`. They expose Actuator health and `/actuator/prometheus`.

```text
Spring observations → Micrometer OTel bridge → OTLP/HTTP → Tempo
/actuator/prometheus ← Prometheus ← Grafana
Tempo traces ────────────────────────────────↑
ECS JSON logs → container stdout
```

### Environment behavior

| Environment | Trace endpoint | Sampling | Logging |
|---|---|---:|---|
| Local Compose | `http://tempo:4318/v1/traces` | `1.0` | ECS JSON, environment `local` by default |
| Helm | Empty by default; export starts only when configured | `0.1` default when export is enabled | ECS JSON when `observability.structuredLogging=true` |

Prometheus defines 13 scrape jobs: 12 host-published applications through `host.docker.internal`, plus the internal assistant service on the shared Compose network. Grafana provisions non-editable Prometheus and Tempo datasources, Prometheus exemplar links to Tempo, and seven read-only dashboards:

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
| `agricore_outbox_backlog` | Unpublished farm, crop-cycle, work, or harvest outbox rows |
| `agricore_kafka_dlq_attempts_total{consumer=...}` | Records handed to DLT recovery; not DLT depth or confirmed publish success |
| `agricore_harvest_processing_seconds_*` | Harvest completion latency histogram and timer series by outcome |
| `agricore_inventory_reservations_total` | Reservation outcomes |
| `agricore_inventory_harvest_events_total` | Applied and duplicate harvest events |
| `agricore_iot_readings_total` | Accepted sensor readings |
| `agricore_iot_alerts_total` | Created and suppressed alert outcomes |
| `agricore_iot_open_alerts` | Current open alert count |
| `agricore_sales_sagas_total` | Sales saga terminal outcomes |
| `agricore_assistant_generations_total` | Completed, failed, and cancelled generations |

There is no Loki or other centralized log backend. ECS stdout is collector-ready but remains container-local. Tempo uses non-persistent container storage with 48-hour configured retention in the local stack.

## 9. Deployment scope

| Environment | Repository mechanism | Scope |
|---|---|---|
| Local | `docker-compose.yml` plus `docker-compose.observability.yml` | Infrastructure, 13 applications, console, Tempo, Prometheus, Grafana |
| Cluster | `infrastructure/helm/agricore` | 13 application Deployments/Services, console, optional Ingress, assistant database Job |
| CI | GitHub Actions | Build/test, frontend, secret, Compose, Helm, CodeQL, Trivy, and gated publishing workflows |

The Helm chart expects external PostgreSQL, Redis, and Kafka services and a pre-created database credential Secret. It does not install Tempo, Prometheus, Grafana, or a log backend. These repository mechanisms do not prove a production cluster is deployed.

## 10. Non-goals

- No Eureka or Consul.
- No shared JPA entities across services.
- No Debezium until polling outbox is insufficient.
- No MinIO or other object-storage integration is implemented.
- No centralized log aggregation is provisioned.

## References

- [Observability ADR](../adr/0010-observability-stack.md)
- [Local operations](../runbooks/local-operations.md)
- [Security review](../security/SECURITY_REVIEW.md)
- [Microservices authorization model](../security/microservices-authz.md)
- OpenAPI contracts under `contracts/openapi/`
- AsyncAPI contracts under `contracts/asyncapi/`
