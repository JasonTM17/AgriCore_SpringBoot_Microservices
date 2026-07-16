# JWT + Gateway E2E proof (local)

Date: 2026-07-16 (refreshed after skeptic cutover)

Infrastructure: Postgres `:5434`, Redis `:6380`, Kafka `:9092` (`docker compose -f docker-compose.infrastructure.yml`).

Apps: identity, farm, crop-catalog, crop-cycle, work, inventory, harvest, traceability, api-gateway as `java -jar` with `AGRICORE_DEV_MODE=false`.

Script: `scripts/e2e-happy-path.ps1` (Bearer JWT via gateway, legal crop-cycle stages, harvest outbox → Kafka consumers).

## Core slice (gateway JWT) — `core-slice.http.log`

| Step | Result |
|------|--------|
| `POST /api/v1/auth/login` | access token issued |
| `POST /api/v1/farms` | farm created (ACTIVE) |
| `POST /api/v1/farms/{id}/plots` | plot created (AVAILABLE) |
| `POST /api/v1/crop-cycles` | cycle PLANNED |
| Stage `LAND_PREPARATION` → `SOWING` → `GROWING` | OK |
| Illegal jump to `COMPLETED` | **409** |

## Full happy path — `e2e-flow.log`

| Check | Result |
|-------|--------|
| Gateway JWT end-to-end | OK |
| Legal stage graph only | OK |
| Harvest complete + outbox event id | OK |
| Kafka inventory consumer | `sku=COFFEE-ROBUSTA onHand=90` |
| Public QR after Kafka projection | `CAPHER-*` product=`Ca phe Robusta` **farm=E2E Dak Lak Farm plot=P1** |
| Unsigned forged JWT against domain services | **401** (prior capture) |

## Security notes

- Domain services validate RS256 JWT via identity JWKS (`libs/common-security`).
- Sales→inventory client forwards caller Bearer JWT (X-Dev only when `agricore.security.dev-mode=true`).
- `HarvestCompleted.v1` payload includes `farmName`, `plotCode`, `productName`, `careSummary` for QR projection.
