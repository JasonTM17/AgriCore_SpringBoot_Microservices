# 8. Assistant service and web console

Date: 2026-07-18

## Status

Accepted

## Context

AgriCore shipped as backend microservices without a first-party UI or assistant. Operators need a Vietnamese console and a safe read-oriented chat surface that does not invent APIs or join databases across services.

## Decision

1. Host `apps/agricore-console` (React/Vite) in the monorepo with a dedicated image; edge serves static UI and same-origin `/api` to the gateway.
2. Browser auth uses identity `/api/v1/auth/web/*` with HttpOnly refresh cookies; access tokens stay in memory.
3. Add `assistant-service` with its own Postgres schema, conversation ownership, idempotent generations, and SSE event replay. Default provider is `none` so boot never requires cloud keys.
4. UI enables only controller-verified capabilities; missing list/history/aggregate endpoints are explicit API gaps.

## Consequences

### Positive

- Clear FE/BE images and ports.
- Safe assistant defaults for portfolio and local stacks.
- Contract honesty reduces FE/BE drift bugs.

### Negative

- Full OpenAI streaming adapter is still simplified (test provider stands in when a key is present for demos).
- Farm membership IDOR hardening remains a follow-up beyond role-scoped RBAC already on domain services.

### Neutral

- Spring AI 2.x is intentionally avoided; provider port isolates future adapter swaps.

## Alternatives considered

- Embed chat in gateway — rejected (wrong ownership and blast radius).
- RAG/pgvector day-one — deferred (no corpus/ingestion pipeline).
- Separate frontend repo — deferred for delivery speed; images remain separate.

## References

- `plans/260718-1232-agricore-web-assistant/plan.md`
- `contracts/openapi/assistant-service.v1.yaml`
- `apps/agricore-console/README.md`
