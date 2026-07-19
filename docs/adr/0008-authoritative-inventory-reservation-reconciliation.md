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

- Prevents Inventory from recording fulfilled stock while Sales records a cancelled order.
- Makes the Inventory release response body a required service contract.
- Fails closed when the remote state is uncertain; operations must reconcile later using the retained reservation reference and non-terminal Sales state.
