# 6. Sales inventory saga orchestration

**Date:** 2026-07-16  
**Status:** Accepted

## Context

Sales orders need inventory reservation without a distributed ACID transaction.

## Decision

Use an **orchestration saga** inside sales-service with persistent `order_sagas` state:
CreateOrder → ReserveInventory (HTTP to inventory) → Confirm.
On reserve failure: OUT_OF_STOCK / CANCELLED. On later failure: release reservation then cancel.

## Consequences

- Positive: clear saga state, compensation path, correlationId
- Negative: temporary inconsistency window; requires inventory availability
- Alternatives: choreography-only events (harder debugging for order UX)
