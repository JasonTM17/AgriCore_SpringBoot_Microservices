# JWT + Gateway E2E proof (local)

Date: 2026-07-16 (full Compose evidence refreshed 2026-07-26)

Infrastructure: Postgres `:5434`, Redis `:6380`, Kafka `:9092`, MQTT, MinIO, and Mailpit through the full Compose stack.

Apps: all 13 Spring containers, the React console, and API gateway with `AGRICORE_DEV_MODE=false`.

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
| Work `CREATED` → `ASSIGNED` → `IN_PROGRESS` → `COMPLETED` | OK |

## Full happy path — `e2e-flow.log`

| Check | Result |
|-------|--------|
| Gateway JWT end-to-end | OK |
| Legal stage graph only | OK |
| Harvest complete + outbox event id | OK |
| Kafka inventory consumer | Aggregate `COFFEE-ROBUSTA` stock increased by exactly 90 kg |
| Public QR after Kafka projection | `CAPHER-*` product=`Ca phe Robusta` **farm=E2E Dak Lak Farm plot=P1** |
| Republished HarvestCompleted | Inventory and traceability stock/projection ledgers remain single-applied |
| Duplicated SalesOrderConfirmed notification | One persisted in-app notification for the stable event ID |
| Wrong-version HarvestCompleted | Both projection consumers route the event to `agricore.harvest.events.DLT` |

## Security notes

- Domain services validate RS256 JWT via identity JWKS (`libs/common-security`).
- Sales→inventory client uses the internal service token for recovery-safe calls and forwards a caller Bearer JWT when present.
- Captured `HarvestCompleted.v1` behavior at the 2026-07-26 evidence refresh:
  authoritative `farmId` accompanies `warehouseId`; public projection fields
  include `farmName`, `plotCode`, `productName`, and `careSummary` when supplied.
- The script includes invalid-token checks at both the gateway and direct farm-service boundary.
- The historical local bundle is summarized in
  [release verification 2026-07-26](./release-verification-2026-07-26.md). It
  predates the final remediation set and does not claim either a current
  clean-revision pass or a continuously deployed production environment. See
  [v1.0.0 release manifest](../releases/v1.0.0.md) for the current
  source-release criteria.
