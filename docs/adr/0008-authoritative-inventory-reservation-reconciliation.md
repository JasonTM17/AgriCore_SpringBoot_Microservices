# 8. Authoritative inventory reservation reconciliation

**Date:** 2026-07-20
**Status:** Accepted

## Context

[ADR-0006](./0006-sales-saga-orchestration.md) compensates a failed sales confirmation by releasing its inventory reservation. A confirm request can commit inventory and still lose its HTTP response. Treating every successful release request as a released hold would then cancel a fulfilled order.

## Decision

Sales must inspect the reservation state returned by Inventory:

- `RELEASED` means compensation completed. Automatic compensation marks the order `CANCELLED` with saga step `COMPENSATED`; manual reconciliation retains its audit markers `RECONCILED` / `RELEASED`.
- `FULFILLED` means stock was already committed; mark the order `CONFIRMED` and saga `COMPLETED`.
- If the release call fails or its response is empty or unrecognized, Sales does not apply a new terminal order state. Automatic compensation therefore remains `STOCK_RESERVED` with saga step `COMPENSATION_PENDING` for operator reconciliation.

The manual `RELEASE` reconciliation path applies the same authoritative order-state mapping while retaining its distinct reconciliation audit step.

## Consequences

### Positive

- Prevents Inventory from recording fulfilled stock while Sales records a
  cancelled order.
- Keeps Inventory as the only owner of reservation truth.

### Negative

- The Inventory release response body becomes a required service contract.
- Uncertain remote state retains non-terminal Sales work for later reconciliation.

### Neutral

- Reconciliation is an explicit operator/application action rather than an
  inferred state change.

## Trade-offs

Sales prefers a visible pending state over an incorrect terminal state. This
increases operational follow-up but prevents silent stock/order divergence.

## Alternatives considered

- **Assume a successful release request means `RELEASED`:** rejected because the
  reservation may already be fulfilled.
- **Cancel on any confirmation exception:** rejected because a committed
  Inventory response can be lost in transit.
- **Query Inventory tables directly:** rejected because it violates database
  ownership.
- **Distributed transaction:** rejected because it couples both services and
  still requires failure recovery at network boundaries.

## References

- [Sales saga ADR](0006-sales-saga-orchestration.md)
- [Inventory reservation saga diagram](../diagrams/inventory-reservation-saga.md)
- [Inventory OpenAPI contract](../../contracts/openapi/inventory-service.v1.yaml)
