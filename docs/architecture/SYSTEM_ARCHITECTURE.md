# AgriCore System Architecture

**Last updated:** 2026-07-17  
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
[Web Admin / Mobile]
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
| identity | users, roles, tokens | UserRegistered via outbox+Kafka poller | — | Publisher wired 2026-07-26 |
| farm | farms, areas, plots | Farm* via outbox+Kafka poller | — | Publisher wired 2026-07-17 |
| crop-catalog | crops, varieties | — | — | REST catalog only |
| crop-cycle | cycles, stages | CropCycle* via outbox+Kafka poller | — (read models optional) | Publisher wired 2026-07-17 |
| work | tasks | WorkTask* via outbox+Kafka poller | — | Publisher wired 2026-07-17 |
| harvest | harvest batches | HarvestCompleted via outbox+Kafka | — | **Primary event producer** |
| inventory | stock, reservations | — (outbox table unused) | HarvestCompleted (Kafka, idempotent+DLT) | REST reserve/release/**confirm** |
| traceability | public timeline / QR | — | HarvestCompleted (Kafka, idempotent+DLT) | Public read model |
| iot | devices, readings, alerts | — | — | REST only; Sensor* events planned |
| sales | orders, saga | — | Inventory via sync HTTP | Saga: reserve → **confirm** → CONFIRMED |
| notification | delivery log | — | UserRegistered (Kafka, idempotent+DLT) | REST sink + welcome notification; NotificationRequested has no producer |

Empty “planned” rows are intentional honesty for portfolio reviewers — do not claim full event mesh.

## 5. Communication Patterns

| Need | Pattern |
|------|---------|
| Sync request/response (auth, CRUD) | REST via Gateway |
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
- Service-to-service: JWT with audience claims (phase 7+)
- Passwords: BCrypt
- Refresh tokens: opaque, hashed, rotated, revocable
- Secrets: env / K8s Secret only

## 8. Observability

Every service exposes:

- `GET /actuator/health`
- `GET /actuator/prometheus`
- Structured JSON logs with `traceId`, `correlationId`
- OTel traces exported to Tempo/Jaeger

## 9. Deployment

| Env | Mechanism |
|-----|-----------|
| Local | Docker Compose |
| Cluster | Kubernetes + Helm |
| CI | GitHub Actions |

## 10. Non-Goals (YAGNI)

- No Eureka / Consul in v1
- No shared JPA entities across services
- No Debezium until polling outbox proven insufficient
- No full frontend app in backend repo (API-first portfolio)

## References

- ADRs under `docs/adr/`
- OpenAPI under `contracts/openapi/`
- AsyncAPI under `contracts/asyncapi/`
