# Inventory Service

## Purpose

Owns farm-scoped warehouses, inventory items, expiry-aware batches, movements,
and reservation truth. It consumes Harvest completion idempotently and serves
the internal reserve/lookup/confirm/release boundary used by Sales and Work.

## API and events

The [OpenAPI contract](../../contracts/openapi/inventory-service.v1.yaml)
covers warehouse/item creation, stock in/out, reservations, business-reference
lookup, confirm/release, item/warehouse reads, pageable movements, and guarded
Harvest event acknowledgement/repair paths.

Consumed: `HarvestCompleted.v1` from `agricore.harvest.events`, with the
processed marker and stock mutation in one transaction.

Published through the outbox: `InventoryReserved.v1`,
`InventoryReservationFailed.v1`, `InventoryReleased.v1`, `StockAdded.v1`, and
`StockDeducted.v1`. See
[AsyncAPI](../../contracts/asyncapi/agricore-events.yaml).

## Invariants and configuration

Reservations and dispatch allocate eligible lots in FEFO order; expired lots
are not newly allocated. Aggregate balance, batch allocations, movements, and
optimistic/pessimistic locks enforce stock correctness. Core variables are
`INVENTORY_PORT`, `POSTGRES_*`, `KAFKA_BOOTSTRAP_SERVERS`,
`FARM_SERVICE_URL`, `INVENTORY_INTERNAL_SERVICE_TOKEN`,
`IDENTITY_JWKS_URI`, and `JWT_ISSUER`.

## Run and verify

```bash
./mvnw -B -pl services/inventory-service -am test
./mvnw -pl services/inventory-service spring-boot:run
```

- Legacy warehouse/processed-event rows without verified farm scope fail
  closed until explicitly mapped.
- Replay Harvest DLT records only with the original stable event ID.
- Reconcile reservation state through the owning API, never direct Sales SQL.

See the [reservation saga ADR](../../docs/adr/0008-authoritative-inventory-reservation-reconciliation.md)
and [Kafka runbook](../../docs/runbooks/kafka-dlq.md).
