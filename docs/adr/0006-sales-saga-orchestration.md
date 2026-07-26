# 6. Sales inventory saga orchestration

**Date:** 2026-07-16  
**Status:** Accepted

## Context

Sales orders need inventory reservation without a distributed ACID transaction.

## Decision

Use an **orchestration saga** inside sales-service with persistent `order_sagas` state:
CreateOrder → bounded ReserveInventory (HTTP to inventory) → Confirm.
The request path has bounded connect/read timeouts. Ambiguous outcomes, confirmation failures,
and compensation failures move to `RETRY_SCHEDULED`; a scheduled recovery worker claims
leases with exponential backoff, reconciles inventory by business reference, and marks
exhausted work `TIMED_OUT` for manual reconciliation.

## Consequences

- Positive: clear saga state, compensation path, correlationId, bounded request latency, durable restart recovery
- Negative: temporary inconsistency window; requires inventory availability and operator review for exhausted ambiguity
- Alternatives: choreography-only events (harder debugging for order UX)
