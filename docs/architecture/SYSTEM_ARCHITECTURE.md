# AgriCore System Architecture

**Last updated:** 2026-07-19

**Status:** Active (implementation matrix honest — see §4)

## 1. Purpose

AgriCore manages the full agricultural production chain for an enterprise: farms → crop cycles → field work → harvest → inventory → sales → QR traceability, plus IoT monitoring and notifications.

## 2. Style

- **Microservices** by business capability (not by table)
- **Database per service** — no cross-service SQL joins
- **Hexagonal / Clean Architecture** inside each service
- **Event-driven** integration via Apache Kafka
- **Transactional Outbox** for reliable publish
- **Idempotent consumers** for at-least-once delivery
- **API Gateway** as single external entry

## 3. Context Diagram

```text
[AgriCore Console / API clients]
        |
   [API Gateway :8080]
        |
   +----+----+----+----+----+----+
   |    |    |    |    |    |    |
 Identity Farm Crop Work Harvest Inventory Traceability ...
   |    |    |    |    |    |    |
   +----+----+---- Kafka ----+----+
                    |
              [Notification]
                    |
         [Postgres x N] [Redis] [MinIO]
```

## 4. Service Boundaries

**Legend:** Implemented in code today vs planned/schema-only.

| Service | Owns | Publishes (runtime) | Consumes (runtime) | Notes |
|---------|------|---------------------|--------------------|-------|
| identity | users, roles, tokens | — (outbox table unused) | — | UserRegistered planned |
| farm | farms, areas, plots | Farm* via outbox+Kafka poller | — | Publisher wired 2026-07-17 |
| crop-catalog | crops, varieties | — | — | REST catalog only |
| crop-cycle | cycles, stages | CropCycle* via outbox+Kafka poller | — (read models optional) | Publisher wired 2026-07-17 |
| work | tasks | WorkTask* via outbox+Kafka poller | — | Publisher wired 2026-07-17 |
| harvest | harvest batches | HarvestCompleted via outbox+Kafka | — | **Primary event producer** |
| inventory | stock, reservations | — (outbox table unused) | HarvestCompleted (Kafka, idempotent+DLT) | REST reserve/release/**confirm** |
| traceability | public timeline / QR | — | HarvestCompleted (Kafka, idempotent+DLT) | Public read model |
| iot | devices, readings, alerts | — | — | REST only; Sensor* events planned |
| sales | orders, saga | — | Inventory via sync HTTP | Saga: reserve → **confirm** → CONFIRMED |
| notification | delivery log | — | — | REST sink; Kafka NotificationRequested planned |

Empty “planned” rows are intentional honesty for portfolio reviewers — do not claim full event mesh.

## 5. Communication Patterns

| Need | Pattern |
|------|---------|
| Sync request/response (auth, CRUD) | REST via Gateway |
| Cross-service farm authorization | Authenticated REST to farm-service with the caller bearer token |
| Domain facts others may need | Kafka domain events |
| Dual DB+message write | Transactional Outbox |
| Cross-service transaction | Saga (sales inventory) |
| Aggregated public view | Local read model in Traceability |

## 6. Internal Service Layers

```text
api → application → domain ← infrastructure
```

- Controllers: HTTP only, no business rules
- Application services: use cases, transactions
- Domain: models, policies, pure exceptions
- Infrastructure: JPA, Kafka, Redis, security adapters

## 7. Security Architecture

- External clients → Gateway → JWT validation (JWKS from identity)
- Gateway and servlet domain services validate RS256 JWTs against identity-service JWKS, including issuer and `agricore-api` audience validation.
- Gateway routing preserves the caller bearer token. `libs/farm-access-client` forwards that token from crop-cycle, work, harvest, and IoT to farm-service; it does not substitute a service identity.
- `farm_memberships` is the authoritative JWT-subject-to-farm ownership mapping. Farm creation grants the creator the initial membership in the same transaction. Membership grants scope, not a JWT role.
- `ROLE_SYSTEM_ADMIN` is the explicit global override. Other callers need both the controller role, where required, and membership in the target farm.
- Farm-service resolves farms, plots, and farm/plot pairs for downstream guards. Plot resolution masks missing, inaccessible, and mismatched plots as `404`.
- Crop-cycle, work, harvest, and IoT check request or stored resource IDs before returning protected data or committing a mutation. Farm-access outages, unexpected statuses, invalid responses, or missing request authentication fail closed as `503 FARM_ACCESS_UNAVAILABLE`.
- Dev identity headers are accepted only when `agricore.security.dev-mode=true`; compose and Helm defaults set dev mode off.
- Passwords: BCrypt
- Refresh tokens: opaque, hashed, rotated, revocable
- Secrets: env / K8s Secret only

See [Microservices authorization model](../security/microservices-authz.md) for endpoint and failure semantics.

## 8. Observability

Service configurations expose Actuator health and Prometheus endpoints. The repository contains Tempo configuration, but this review found no application-side OpenTelemetry exporter wiring; runtime trace export is not claimed here.

- `GET /actuator/health`
- `GET /actuator/prometheus`

## 9. Deployment

| Env | Mechanism |
|-----|-----------|
| Local | Docker Compose |
| Cluster | Kubernetes + Helm |
| CI | GitHub Actions |

These rows describe repository mechanisms, not evidence that a production cluster is currently deployed.

## 10. Non-Goals (YAGNI)

- No Eureka / Consul in v1
- No shared JPA entities across services
- No Debezium until polling outbox proven insufficient

## References

- [Security review](../security/SECURITY_REVIEW.md)
- [Microservices authorization model](../security/microservices-authz.md)
- ADRs under `docs/adr/`
- OpenAPI under `contracts/openapi/`
- AsyncAPI under `contracts/asyncapi/`
