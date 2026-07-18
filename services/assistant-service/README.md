# assistant-service

## Purpose

Persisted, owner-scoped chatbot service for AgriCore. Boots without an LLM key (`provider=none`), streams generation events over authenticated SSE when a provider is available, and refuses unsafe prompts in the deterministic test provider.

## API surface

- `GET /api/v1/assistant/capabilities`
- `GET|POST /api/v1/assistant/conversations`
- `GET /api/v1/assistant/conversations/{id}/messages`
- `POST /api/v1/assistant/conversations/{id}/generations`
- `GET /api/v1/assistant/conversations/{id}/generations/{gid}/events` (SSE)

## Env vars

| name | required | default | description |
|---|---|---|---|
| `ASSISTANT_PORT` | no | `8093` | HTTP port |
| `ASSISTANT_PROVIDER` | no | `none` | `none` \| `test` \| `openai` \| `ollama` |
| `ASSISTANT_OPENAI_API_KEY` | no | empty | Provider key |
| `ASSISTANT_OPENAI_BASE_URL` | no | OpenAI | OpenAI-compatible base URL |
| `ASSISTANT_OPENAI_MODEL` | no | `gpt-4o-mini` | Model id |
| `IDENTITY_JWKS_URI` | yes (prod) | local JWKS | JWT verification |

## Run locally

```bash
./mvnw -pl services/assistant-service -am spring-boot:run
```

## Test

```bash
./mvnw -pl services/assistant-service -am test
```

## Runbook

- Generation 503 with reason “no provider”: expected when `ASSISTANT_PROVIDER=none`.
- For local demos without cloud keys: `ASSISTANT_PROVIDER=test`.
- Database: `agricore_assistant` (see `infrastructure/docker/postgres/init-databases.sql`).
