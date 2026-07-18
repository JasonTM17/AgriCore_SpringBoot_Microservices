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

## Container

- Image base: `nginxinc/nginx-unprivileged:1.27-alpine` (uid **101**, listen **8080**).
- `GATEWAY_UPSTREAM` env (default `api-gateway:8080`; Helm `agricore-gateway:8080`).
- Compose maps host `5173` → container `8080`. Helm Service port `8080`.

## Runbook

- Login fails with `ORIGIN_FORBIDDEN`: add origin to identity `agricore.security.web-allowed-origins`.
- Refresh loops: clear `agricore_refresh` cookie path `/api/v1/auth/web`.
- Assistant unavailable: set `ASSISTANT_PROVIDER=test` (local) or configure OpenAI-compatible key.
- Helm crashLoop on console: ensure `console.port=8080` and `console.runAsUser=101` (non-root cannot bind :80).
