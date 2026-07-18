# AgriCore Operations Console

## Purpose

Vietnamese-first operations console for AgriCore microservices. Calls the API gateway at `/api` (dev proxy or same-origin Nginx) using in-memory access tokens and HttpOnly refresh cookies.

## API surface

- Auth: `/api/v1/auth/web/*`, `/api/v1/users/me`
- Farms, crop-cycles, work-tasks, harvests, inventory (by id), sales (by id), IoT, admin users
- Public trace: `/public/api/v1/traceability/{code}`
- Assistant: `/api/v1/assistant/**` (SSE generations)

Missing list/aggregate contracts are labeled in UI (`LIVE_API_CAPABILITIES`), never faked.

## Env vars

| name | required | default | description |
|---|---|---|---|
| `AGRICORE_GATEWAY_URL` | no | `http://localhost:8080` | Vite dev proxy target |

## Run locally

```bash
pnpm --store-dir D:/caches/pnpm-store install
pnpm dev
```

## Test

```bash
pnpm lint && pnpm typecheck && pnpm test && pnpm build
```

Coverage focus: auth session, role matrix, API client single-flight refresh, live capability guards.

## Runbook

- Login fails with `ORIGIN_FORBIDDEN`: add origin to identity `agricore.security.web-allowed-origins`.
- Refresh loops: clear `agricore_refresh` cookie path `/api/v1/auth/web`.
- Assistant unavailable: set `ASSISTANT_PROVIDER=test` (local) or configure OpenAI-compatible key.
