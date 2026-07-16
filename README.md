# AgriCore – Agricultural Enterprise Management Platform

Portfolio-grade **Java 21 / Spring Boot microservices** platform for enterprise crop production: farms, plots, crop catalog, seasons, field work, harvest, inventory, IoT, sales, and QR traceability.

## Status

**Phase 0–2 foundation live:** monorepo, common library, Docker infrastructure, Identity, Farm, Crop Catalog, API Gateway.

| Service | Port | Status |
|---------|------|--------|
| api-gateway | 8080 | Live |
| identity-service | 8081 | Live |
| farm-service | 8082 | Live |
| crop-catalog-service | 8083 | Live |
| crop-cycle, work, inventory, harvest, traceability, iot, sales, notification | — | Planned |

## Architecture

```text
Client → API Gateway (:8080)
            ├─ Identity (:8081)  JWT RS256 + JWKS, refresh rotation, RBAC
            ├─ Farm (:8082)      Farms & plots + transactional outbox
            └─ Crop Catalog (:8083) Seeded Vietnam crop varieties
```

- **Database per service** (PostgreSQL)
- **No shared domain models** across services
- **Kafka-ready** event envelope + outbox tables
- Docs: [System Architecture](docs/architecture/SYSTEM_ARCHITECTURE.md) · [Implementation Plan](docs/IMPLEMENTATION_PLAN.md) · [ADRs](docs/adr/)

## Prerequisites

- JDK 21+ (compile target 21)
- Maven 3.9+
- Docker / Docker Compose

## Quick start

### 1. Infrastructure

```powershell
# Windows
.\scripts\dev-up.ps1
```

```bash
# Linux/macOS
./scripts/dev-up.sh
```

Starts PostgreSQL (multi-DB), Redis, Kafka, Kafka UI (`http://localhost:8088`).

### 2. Build & test

```bash
mvn verify
```

### 3. Run services locally

```bash
# terminals
mvn -pl services/identity-service spring-boot:run
mvn -pl services/farm-service spring-boot:run
mvn -pl services/crop-catalog-service spring-boot:run
mvn -pl services/api-gateway spring-boot:run
```

### 4. Full Docker stack

```bash
docker compose up --build
```

Gateway: `http://localhost:8080`

## Auth examples

```bash
# Register
curl -s -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"manager@agricore.local","password":"Secret123!","fullName":"Farm Manager"}'

# Login
curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"manager@agricore.local","password":"Secret123!"}'
```

JWKS: `GET /.well-known/jwks.json`

## Default roles

`SYSTEM_ADMIN` · `FARM_MANAGER` · `AGRONOMIST` · `FIELD_WORKER` · `WAREHOUSE_MANAGER` · `SALES_STAFF` · `AUDITOR`

New users receive `FIELD_WORKER`. Promote via `PATCH /api/v1/admin/users/{id}/roles` (admin only).

## Seeded crops

Robusta coffee, Ri6 durian, red dragon fruit, ST25 rice, lettuce, tomato, black pepper.

## Project layout

```text
services/          Spring Boot microservices
libs/common-lib/   API errors, event envelope (no domain)
contracts/         OpenAPI / event schemas
infrastructure/    Docker, K8s (later), monitoring
docs/              Architecture, ADRs, runbooks
plans/             CK implementation plans
```

## Security notes

- Passwords: BCrypt (cost 12 production)
- Access tokens: short-lived RS256 JWT
- Refresh tokens: opaque, hashed, rotated; reuse revokes family
- Account lockout after failed logins
- Login rate limit via Redis
- **Never commit** `.env`, private keys, or production secrets

## CI

GitHub Actions: build + test on push/PR (`.github/workflows/ci.yml`).

## License

MIT (see `LICENSE` when published).
