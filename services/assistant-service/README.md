# Assistant Service

## Purpose

Provides an authenticated, persisted, read-only assistant behind the API
Gateway. It owns conversations, messages, generation leases, ordered fetch-SSE
replay, retention timestamps, and redacted tool evidence in
`agricore_assistant`. Redis enforces request/token budgets. No Kafka path is
implemented. Optional curated RAG retrieves bounded, cited AgriCore knowledge
from indexed tables in the same assistant-owned database.

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
`ASSISTANT_RAG_*`. Provider credentials belong in local ignored environment
files or deployment Secrets only.

DeepSeek V4 Flash uses the existing OpenAI-compatible provider boundary:

```dotenv
ASSISTANT_PROVIDER=openai
ASSISTANT_PROVIDER_MODEL=deepseek-v4-flash
ASSISTANT_PROVIDER_BASE_URL=https://api.deepseek.com
ASSISTANT_PROVIDER_API_KEY=<runtime-secret>
ASSISTANT_RAG_ENABLED=true
```

Confirm the account-visible model identifier through DeepSeek's
[model-list endpoint](https://api-docs.deepseek.com/api/list-models) before a
production rollout.

Provider and RAG remain disabled by default. Retrieval tokenizes at most 12
query terms, returns at most four `KB-*` citations, uses a two-second database
query timeout, and merges results with authorized farm facts under the existing
25-fact/24,000-character evidence ceilings. The curated migration is
read-only at runtime; arbitrary URL fetch and document upload are not exposed.
If retrieval fails after authorized farm facts were collected, those facts are
retained as partial evidence and the RAG degradation reason is audited.
Follow the
[database rollback procedure](../../docs/deployment-guide.md#database-change-and-rollback)
before downgrading to a binary that predates persisted `KNOWLEDGE` evidence.

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
