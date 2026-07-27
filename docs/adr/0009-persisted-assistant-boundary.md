# 9. Persisted assistant and same-origin web boundary

**Date:** 2026-07-22

**Status:** Accepted

## Context

The console needs a durable assistant without making a model provider an
authorization or data-access boundary. Browser clients also need one origin so
cookies, fetch-SSE, CSP, and gateway routing remain consistent.

## Decision

1. Keep the assistant in a separate Spring Boot service with its own PostgreSQL
   database. Persist conversations, messages, generation state, event sequence,
   retention timestamps, and redacted tool evidence.
2. Route `/api/v1/assistant/**` through the gateway. Serve console and API from
   one edge; do not host-publish the assistant service in Compose.
3. Make provider `none` the default. Provider type, model, base URL, and API key
   are deployment inputs from environment variables or Kubernetes Secrets.
4. Permit only authenticated, read-only farm calls to an allowlisted host.
   Forward caller JWT, bound rows/bytes/time, validate responses, and fail closed.
5. Permit opt-in retrieval only from curated, versioned knowledge chunks in the
   assistant-owned database. Use indexed terms, bounded top-k results, citation
   identifiers stable within each persisted generation snapshot, prepared
   statements, and the same evidence boundary as authorized farm facts.
6. Use idempotent generation submission and ordered fetch-SSE replay. Return safe
   outcome codes instead of raw provider failures or unsafe output.

## Consequences

### Positive

- The assistant boots and is testable without a provider secret.
- Conversation ownership, replay, and budgets are platform-controlled.
- Model output cannot directly mutate farm state.
- Retrieval adds grounded product and operations context without another
  credential, embedding provider, or cross-service database read.

### Negative

- PostgreSQL and Redis are required for durable state and traffic budgets.
- Provider outages produce limited/unavailable outcomes.
- Same-origin proxies must preserve long-lived SSE and disable buffering.

### Neutral

- Arbitrary URL fetching, autonomous writes, user-controlled RAG ingestion, and
  cross-database joins remain outside the accepted boundary.

## Trade-offs

The assistant sacrifices autonomous actions, arbitrary ingestion, and semantic
embedding search for a smaller trust boundary, replayable evidence, indexed
deterministic retrieval, and operation without an external embedding key.

## Alternatives considered

- **Browser calls the provider directly:** rejected because credentials, data
  access, and policy enforcement would leave the backend boundary.
- **Stateless chat endpoint:** rejected because reconnect, idempotency, ownership,
  and audit requirements need durable state.
- **Assistant writes domain services:** rejected until explicit approval,
  confirmation, authorization, and compensation contracts exist.
- **Provider required at startup:** rejected because local and CI environments
  must remain deterministic and secret-free.

## Verification

- Assistant tests cover ownership, idempotency, replay, refusal, tool evidence,
  curated retrieval, H2/PostgreSQL migration, injection-safe query binding, and
  budget failure.
- Frontend tests and production CSP/build gates cover fetch-SSE behavior.
- Compose and Helm keep provider credentials as deployment inputs.

## References

- [Assistant OpenAPI contract](../../contracts/openapi/assistant-service.v1.yaml)
- [Assistant database runbook](../runbooks/assistant-database-provisioning.md)
- [API gateway ADR](0014-api-gateway-and-same-origin-edge.md)
