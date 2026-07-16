# 4. Transactional Outbox with Polling Publisher

**Date:** 2026-07-16  
**Status:** Accepted

## Context

Publishing Kafka inside the same method as DB write risks dual-write inconsistency.

## Decision

Use **Transactional Outbox**: write aggregate + outbox row in one DB transaction; a scheduled poller publishes unpublished rows to Kafka and marks them published. Debezium CDC deferred until scale requires it.

## Consequences

- Positive: correct-by-construction event publish; simple ops
- Negative: poll latency (configurable, default 1s)
- Future: ADR may supersede with CDC
