# AgriCore System Architecture

**Last updated:** 2026-07-16  
**Status:** Active

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

| Service | Owns | Publishes | Consumes |
|---------|------|-----------|----------|
| identity | users, roles, tokens | UserRegistered | — |
| farm | farms, areas, plots | FarmCreated, PlotCreated, PlotStatusChanged | — |
| crop-catalog | crops, varieties | CropCreated | — |
| crop-cycle | cycles, stages | CropCycle* | Plot*, Crop* (read models optional) |
| work | tasks | WorkTask* | CropCycleStageChanged |
| harvest | harvest batches | Harvest* | CropCycle*, Work* |
| inventory | stock, reservations | Stock*, Inventory* | HarvestCompleted, Sales* |
| traceability | public timeline | Traceability* | Harvest*, CropCycle*, Farm*, Work* |
| iot | devices, readings, alerts | Sensor*, Device* | — |
| sales | orders, saga | SalesOrder* | Inventory* responses |
| notification | delivery log | Notification* | NotificationRequested |

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
