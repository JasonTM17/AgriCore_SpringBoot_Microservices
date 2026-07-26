# Codebase Summary

Orientation map for the AgriCore monorepo. Architecture rationale lives in
[System Architecture](architecture/SYSTEM_ARCHITECTURE.md) and the [ADRs](adr/) — this file is the
"where is what" index.

## Layout

```text
services/          12 Spring Boot services, one Maven module each
libs/common-lib/   API error envelope, event envelope, event type constants (no domain types)
libs/common-security/  Shared JWT resource-server config for domain services
contracts/         OpenAPI per service, AsyncAPI for topics, JSON Schema for the event envelope
infrastructure/    Docker (Postgres init), Helm chart, monitoring config, K8s network policy
scripts/           dev-up, JWT key generation, platform verification, e2e happy path
docs/              This documentation set, ADRs, runbooks, security review
plans/             ClaudeKit implementation plans (gitignored — local only)
```

## Services

| Service | Port | Database | Role |
|---------|------|----------|------|
| api-gateway | 8080 | — | Edge routing + JWT validation |
| identity-service | 8081 | `agricore_identity` | Users, roles, tokens, JWKS |
| farm-service | 8082 | `agricore_farm` | Farms and plots |
| crop-catalog-service | 8083 | `agricore_crop_catalog` | Crop varieties |
| crop-cycle-service | 8084 | `agricore_crop_cycle` | Growing cycles and stages |
| work-service | 8085 | `agricore_work` | Field tasks |
| inventory-service | 8086 | `agricore_inventory` | Warehouses, stock, reservations |
| harvest-service | 8087 | `agricore_harvest` | Harvest batches |
| notification-service | 8089 | `agricore_notification` | Notification records |
| iot-service | 8090 | `agricore_iot` | Devices and sensor readings |
| sales-service | 8091 | `agricore_sales` | Customers, orders, inventory saga |
| traceability-service | 8092 | `agricore_traceability` | Public QR trace read model |

Each service has its own README with endpoints, env vars, and a runbook.

## Package layout inside a service

```text
com.agricore.<service>
  api/            controllers, request/response records
  application/    application services (orchestration), outbox writers
  domain/         entities/value objects, domain exceptions, enums
  infrastructure/ persistence (JPA entities + repositories), messaging, security, configuration
```

Rules that hold across services: no cross-service JPA relationships, no shared domain model, no
schema sharing. Services reference each other's aggregates by id only.

## Event flow (runtime, not aspirational)

```text
identity   ──UserRegistered.v1──▶ agricore.identity.events   ──▶ notification (welcome record)
farm       ──Farm*/Plot*────────▶ agricore.farm.events
crop-cycle ──CropCycle*─────────▶ agricore.crop-cycle.events
work       ──WorkTask*──────────▶ agricore.work.events
harvest    ──HarvestCompleted───▶ agricore.harvest.events    ──▶ inventory (stock-in)
                                                             ──▶ traceability (public read model)
sales      ──reserve/confirm───▶ inventory over HTTP (orchestrated saga, no events)
```

Every publisher uses the transactional outbox with a polling publisher. Every consumer dedupes on
`eventId` through a `processed_events` table and routes poison messages to `<topic>.DLT`.

`NotificationRequested.v1`, `Sensor*.v1`, `SalesOrder*.v1`, `Traceability*.v1`, and inventory's
`Stock*.v1` exist as constants in `EventTypes` with **no producer**. They are naming reservations, not
behavior. The current event matrix in
[System Architecture §4](architecture/SYSTEM_ARCHITECTURE.md) is authoritative.

## Cross-cutting concerns

| Concern | Where |
|---------|-------|
| Error envelope | `libs/common-lib` → `ApiError` |
| Event envelope | `libs/common-lib` → `DomainEventEnvelope`, `contracts/event-schemas/` |
| Event type names | `libs/common-lib` → `EventTypes` |
| JWT verification (domain services) | `libs/common-security` → `DomainServiceSecurityConfig` |
| Token issuance + JWKS | `identity-service` → `JwtTokenService` |
| Outbox writer | per service, e.g. `CropCycleOutboxWriter`, `IdentityOutboxWriter` |
| Outbox publisher | per service `infrastructure/messaging/OutboxPublisher` |
| Consumer idempotency | per service `processed_events` table + `ProcessedEventJpaRepository` |
| DLT routing | per service `infrastructure/messaging/KafkaConsumerErrorConfig` |
| Migrations | per service `src/main/resources/db/migration` (Flyway) |

The duplication of outbox/publisher code across services is deliberate: the no-shared-domain rule
outweighs DRY here, and a shared outbox library would couple release cycles.

## Build and test

```bash
./mvnw -B verify                                   # full reactor, coverage reports included
./mvnw -B -pl services/<name> -am test             # one service and its dependencies
```

Coverage is measured by JaCoCo per module (reports under each module's build output) but not yet
enforced; see [project-roadmap.md](project-roadmap.md) for the strict-flip plan.
