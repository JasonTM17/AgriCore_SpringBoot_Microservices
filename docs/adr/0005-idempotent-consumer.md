# 5. Idempotent Consumer via processed_events

**Date:** 2026-07-16  
**Status:** Accepted

## Context

Kafka delivers at-least-once. Inventory must not double stock when `HarvestCompleted.v1` is redelivered.

## Decision

Each consumer with side effects uses table `processed_events (event_id, consumer_name, processed_at)` with primary key `(event_id, consumer_name)`. Business write and insert into `processed_events` share one DB transaction.

## Consequences

- Positive: correct under redelivery; simple to reason about
- Negative: requires eventId on every envelope
- Neutral: Kafka consumer adapter can call the same application method as the sync test endpoint
