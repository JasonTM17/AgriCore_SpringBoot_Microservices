# sales-service

## Purpose

Owns customers and sales orders, and orchestrates the stock side of an order as a saga: reserve
inventory, then confirm it once the order is accepted, releasing on failure. Called by the gateway;
calls inventory-service over HTTP (orchestration, not choreography — ADR-0006).

## API surface

- `POST /api/v1/sales/customers` — create a customer
- `POST /api/v1/sales/orders` — create an order (reserves then confirms inventory)
- `GET /api/v1/sales/orders/{orderId}` — order detail
- `POST /api/v1/sales/orders/{orderId}/reconcile` — release/settle a reservation left stuck by a
  failed order
- Contract: `contracts/openapi/sales-service.v1.yaml`
- Events published: none (`SalesOrderCreated.v1` and siblings exist as constants)
- Events consumed: none — the inventory interaction is synchronous HTTP

## Env vars

| Name | Required | Default | Description |
|------|----------|---------|-------------|
| `SALES_PORT` | no | `8091` | HTTP listen port |
| `POSTGRES_HOST` | no | `localhost` | Database host |
| `POSTGRES_PORT` | no | `5434` | Database port |
| `POSTGRES_USER` | no | `agricore` | Database user |
| `POSTGRES_PASSWORD` | yes in prod | dev value | Database password |
| `INVENTORY_SERVICE_URL` | no | `http://localhost:8086` | Inventory base URL for the saga |
| `JWT_ISSUER` | no | `https://agricore.local/identity` | Expected `iss` claim |
| `IDENTITY_JWKS_URI` | no | identity JWKS URL | Key source for local token verification |
| `AGRICORE_DEV_MODE` | no | `false` | Dev shortcuts; keep `false` outside local work |

Database: `agricore_sales`. The caller's bearer token is forwarded to inventory; `X-Dev` headers are
only used when dev-mode is on.

## Run locally

```bash
./scripts/dev-up.sh
mvn -pl services/identity-service spring-boot:run
mvn -pl services/inventory-service spring-boot:run
mvn -pl services/sales-service spring-boot:run
```

## Test

```bash
./mvnw -B -pl services/sales-service -am test
./mvnw -B -pl services/sales-service -am verify   # adds the JaCoCo report
```

Covers the reserve → confirm path and the reconcile route for stuck reservations. Target once
coverage gating is enforced: ≥ 90% lines / ≥ 85% branches as a critical module.

## Runbook

- **Order created but stock never deducted** — the confirm leg failed; call the reconcile endpoint for
  that order rather than editing inventory rows.
- **Reservation held with no order** — same reconcile path; it exists precisely because a crash between
  reserve and confirm leaves a hold.
- **Inventory unreachable** — orders fail closed rather than committing an unbacked sale; check
  `INVENTORY_SERVICE_URL` and the inventory container's health.
- **Reset local data** — drop and recreate `agricore_sales`, restart to replay migrations.
