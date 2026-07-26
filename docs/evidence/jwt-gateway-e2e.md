# JWT + Gateway E2E proof (local)

Date: 2026-07-16 (evidence scope reviewed 2026-07-26)

Infrastructure: Postgres `:5434`, Redis `:6380`, Kafka `:9092` (`docker compose -f docker-compose.infrastructure.yml`).

Apps: identity, farm, crop-catalog, crop-cycle, work, inventory, harvest, traceability, api-gateway as `java -jar` with `AGRICORE_DEV_MODE=false`.

Script: `scripts/e2e-happy-path.ps1` (gateway/service JWT boundary checks, legal crop-cycle stages, harvest outbox → Kafka consumers, duplicate replay, notification dedupe, and DLT injection).

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
| Republished HarvestCompleted | Inventory and traceability stock/projection ledgers remain single-applied |
| Duplicated SalesOrderConfirmed notification | One persisted in-app notification for the stable event ID |
| Wrong-version HarvestCompleted | Both projection consumers route the event to `agricore.harvest.events.DLT` |

## Security notes

- Domain services validate RS256 JWT via identity JWKS (`libs/common-security`).
- Sales→inventory client uses the internal service token for recovery-safe calls and forwards a caller Bearer JWT when present.
- `HarvestCompleted.v1` payload includes `farmName`, `plotCode`, `productName`, `careSummary` for QR projection.
- The script includes invalid-token checks at both the gateway and direct farm-service boundary. A completed evidence bundle is still required before release because this repository does not claim a continuously deployed environment.
