# inventory-service

## Purpose

Owns warehouses, stock items, and reservations. Consumes harvest completions to add stock, and serves
the reserve → confirm / release flow that sales-service orchestrates. Called by the gateway and by
sales-service; consumes Kafka.

## API surface

- `POST /api/v1/inventory/warehouses` — create a warehouse
- `POST /api/v1/inventory/items` — create a stock item
- `POST /api/v1/inventory/stock-in` — manual stock-in
- `GET /api/v1/inventory/items/{itemId}` — item detail with on-hand and reserved quantities
- `POST /api/v1/inventory/reservations` — reserve quantity for an order
- `POST /api/v1/inventory/reservations/{reservationId}/confirm` — deduct reserved stock
- `POST /api/v1/inventory/reservations/{reservationId}/release` — release a reservation
- `POST /api/v1/inventory/events/harvest-completed` — HTTP fallback for the harvest event
- Contract: `contracts/openapi/inventory-service.v1.yaml`
- Events published: none (`StockAdded.v1` / `InventoryReserved.v1` exist as constants)
- Events consumed: `HarvestCompleted.v1` from `agricore.harvest.events`, idempotent via `processed_events`,
  poison messages routed to `agricore.harvest.events.DLT`

## Env vars

| Name | Required | Default | Description |
|------|----------|---------|-------------|
| `INVENTORY_PORT` | no | `8086` | HTTP listen port |
| `POSTGRES_HOST` | no | `localhost` | Database host |
| `POSTGRES_PORT` | no | `5434` | Database port |
| `POSTGRES_USER` | no | `agricore` | Database user |
| `POSTGRES_PASSWORD` | yes in prod | dev value | Database password |
| `KAFKA_BOOTSTRAP_SERVERS` | no | `localhost:9092` | Broker for the consumer and DLT producer |
| `JWT_ISSUER` | no | `https://agricore.local/identity` | Expected `iss` claim |
| `IDENTITY_JWKS_URI` | no | identity JWKS URL | Key source for local token verification |
| `AGRICORE_DEV_MODE` | no | `false` | Dev shortcuts; keep `false` outside local work |

Database: `agricore_inventory`. Stock rows use optimistic locking so concurrent updates cannot double-count.

## Run locally

```bash
./scripts/dev-up.sh
mvn -pl services/inventory-service spring-boot:run
```

## Test

```bash
./mvnw -B -pl services/inventory-service -am test
./mvnw -B -pl services/inventory-service -am verify   # adds the JaCoCo report
```

`InventoryPostgresIdempotencyTest` runs against a real PostgreSQL container and **fails closed** when
Docker is unavailable — it never silently skips. Target once coverage gating is enforced:
≥ 90% lines / ≥ 85% branches as a critical module.

## Runbook

- **Stock did not increase after a harvest** — check `processed_events` for the `eventId` (already
  processed?), then the consumer logs, then `agricore.harvest.events.DLT`.
- **Reservation stuck in reserved** — sales-service exposes a reconcile endpoint that releases
  reservations orphaned by a failed order; see `services/sales-service/README.md`.
- **Replay DLT** — republish DLT records onto the source topic; the consumer dedupes on `eventId`.
- **Optimistic lock failures under load** — expected contention signal; the caller should retry rather
  than the row being force-updated.
