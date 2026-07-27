# Sales Service

## Purpose

Owns farm-scoped customers, orders, immutable price snapshots, order items, and
the durable Inventory reservation saga. Sales verifies Farm scope, makes a
bounded reserve/confirm attempt, and persists recovery/compensation state
instead of using a cross-service transaction.

## API and events

- `/api/v1/sales/customers`: create and list farm customers.
- `/api/v1/sales/orders`: create and list orders.
- `/api/v1/sales/orders/{orderId}`: scoped order and saga state.
- `/api/v1/sales/orders/{orderId}/reconcile`: explicit manual resolution.

See [OpenAPI](../../contracts/openapi/sales-service.v1.yaml).
Published through the outbox: `SalesOrderCreated.v1`,
`SalesOrderConfirmed.v1`, and `SalesOrderCancelled.v1`; Notification consumes
the confirmed/cancelled events. Sales consumes no Kafka events.

## Saga and configuration

Inventory remains authoritative for reservation truth. Ambiguous calls move to
bounded durable recovery with lease, backoff, business-reference lookup, and
compensation. Exhausted ambiguity becomes operator-visible manual
reconciliation rather than a guessed terminal state.

Core variables are `SALES_PORT`, `POSTGRES_*`, `FARM_SERVICE_URL`,
`INVENTORY_SERVICE_URL`, `INVENTORY_INTERNAL_SERVICE_TOKEN`,
`INVENTORY_*_TIMEOUT`, `SALES_SAGA_RECOVERY_*`,
`KAFKA_BOOTSTRAP_SERVERS`, `IDENTITY_JWKS_URI`, and `JWT_ISSUER`.

## Run and verify

```bash
./mvnw -B -pl services/sales-service -am test
./mvnw -pl services/sales-service spring-boot:run
```

- Do not infer cancellation after a lost confirm response; reconcile Inventory
  `FULFILLED` versus `RELEASED`.
- Do not edit Inventory rows from Sales recovery.

See the [saga ADR](../../docs/adr/0006-sales-saga-orchestration.md),
[authoritative reconciliation ADR](../../docs/adr/0008-authoritative-inventory-reservation-reconciliation.md),
and [AsyncAPI](../../contracts/asyncapi/agricore-events.yaml).
