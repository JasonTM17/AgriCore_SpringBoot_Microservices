# ADR 0009: Persisted Assistant and Same-Origin Web Boundary

- **Status:** Accepted
- **Date:** 2026-07-22
- **Decision owners:** AgriCore platform maintainers

## Context

The web console needs a durable assistant without turning the model provider into an authorization or data-access boundary. Browser clients also need a single origin so cookies, fetch-SSE, CSP, and gateway routing are consistent.

## Decision

1. Keep the assistant in a separate Spring Boot service with its own PostgreSQL database. Persist conversations, messages, generation state, event sequence, retention timestamps, and redacted tool evidence.
2. Route `/api/v1/assistant/**` through the gateway. Serve the console and API from one edge (`/` and `/api`); do not publish assistant-service directly in Compose.
3. Make provider `none` the default. Provider type, model, base URL, and API key are deployment inputs from environment variables or Kubernetes Secrets only.
4. Permit only authenticated, read-only farm reads through an allowlisted host. Forward the caller JWT, cap rows/bytes/time, validate the response shape, and fail closed on downstream or budget errors.
5. Use idempotent generation submission and ordered fetch-SSE replay. Deterministic output checks can refuse unsafe/provider-sensitive output; the UI receives a safe reason code rather than raw provider details.

## Consequences

- The assistant can boot and be smoke-tested without an external AI key.
- PostgreSQL and Redis are runtime dependencies for durable state and bounded traffic; existing volumes require the idempotent provisioning runbook.
- Provider outages degrade to a limited/unavailable user outcome. Autonomous writes, arbitrary URL fetches, RAG ingestion, and cross-database joins remain out of scope.
- Same-origin Nginx/Ingress settings must preserve long-lived SSE connections and disable request buffering for the assistant routes.

## Verification

- Assistant unit/integration tests cover ownership, idempotency, replay, refusal, tool evidence, and budget failure paths.
- Frontend lint, typecheck, 280 tests, and production build pass.
- Compose and Helm config/lint/template checks pass; security scans report no high/critical dependency or secret finding after the PostgreSQL JDBC upgrade to 42.7.12.
