# Assistant Service

## Purpose

Provides an authenticated, persisted, read-only assistant behind the API
Gateway. It owns conversations, messages, generation leases, ordered fetch-SSE
replay, retention timestamps, and redacted tool evidence in
`agricore_assistant`. Redis enforces request/token budgets. No Kafka path is
implemented.

## API

The [OpenAPI contract](../../contracts/openapi/assistant-service.v1.yaml)
defines capabilities; conversation create/read/archive; message history;
generation submit/read/cancel; and ordered generation event replay.

Provider `none` is the safe default. Configured providers cannot grant data
access: tools are separately enabled, read-only, farm-host allowlisted, caller
token forwarding, and bounded by rows, bytes, and time.

## Configuration

Core groups are `ASSISTANT_PROVIDER_*`, `ASSISTANT_BUDGET_*`,
`ASSISTANT_TOOL_*`, `ASSISTANT_STREAM_*`, `ASSISTANT_WORKER_*`, and
`ASSISTANT_*_RETENTION`. Provider credentials belong in local ignored
environment files or deployment Secrets only.

## Run and verify

```bash
./mvnw -B -pl services/assistant-service -am test
./mvnw -pl services/assistant-service spring-boot:run
```

The service is internal in Compose; use `/api/v1/assistant/**` through
`http://localhost:3000`.

- Existing PostgreSQL volume: follow the
  [database provisioning runbook](../../docs/runbooks/assistant-database-provisioning.md).
- `ASSISTANT_BUDGET_UNAVAILABLE`: restore Redis; budgets fail closed.
- SSE reconnect: reuse generation ID and `Last-Event-ID`; do not resubmit work.

See the [assistant boundary ADR](../../docs/adr/0009-persisted-assistant-boundary.md)
and [local operations](../../docs/runbooks/local-operations.md#console-and-assistant).
