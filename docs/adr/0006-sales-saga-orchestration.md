# 6. Sales inventory saga orchestration

**Date:** 2026-07-16  
**Status:** Accepted

## Context

Sales orders need inventory reservation without a distributed ACID transaction.

## Decision

Use an **orchestration saga** inside sales-service with persistent `order_sagas` state:
CreateOrder → bounded ReserveInventory (HTTP to inventory) → Confirm.
Customers and orders persist authoritative `farmId`. Sales verifies that farm
before creation/read/reconciliation and includes it in Inventory reserve,
business-reference lookup, confirm, and release calls. Inventory resolves the
item's warehouse and masks a farm mismatch as not found.
The request path has bounded connect/read timeouts. Ambiguous outcomes, confirmation failures,
and compensation failures move to `RETRY_SCHEDULED`; a scheduled recovery worker claims
leases with exponential backoff, reconciles inventory by business reference, and marks
exhausted work `TIMED_OUT` for manual reconciliation.

## Consequences

- Positive: clear saga state, compensation path, correlation ID, bounded request
  latency, and durable restart recovery.
- Negative: temporary inconsistency window; requires inventory availability and
  operator review for exhausted ambiguity. Additive Sales migrations leave
  pre-scope rows nullable; those rows fail closed until backfilled.

## Trade-offs

Sales owns more recovery state and worker logic, but gains one queryable place
to explain an order's reservation outcome. The bounded synchronous attempt
improves immediate user feedback while persistent reconciliation handles
network ambiguity without holding the HTTP request open.

## Alternatives considered

- **Choreography-only events:** rejected because the order API could not provide
  a bounded reservation result and failure diagnosis would be spread across
  consumers.
- **Distributed transaction:** rejected because Sales and Inventory own
  separate databases and availability boundaries.
- **Unbounded synchronous retries:** rejected because downstream failure would
  consume request threads and still leave ambiguous outcomes on timeout.
