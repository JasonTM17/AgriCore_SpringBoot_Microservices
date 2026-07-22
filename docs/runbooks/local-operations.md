# Local operations runbook

## Start infrastructure

```powershell
.\scripts\dev-up.ps1
# Postgres :5434  Redis :6380  Kafka :9092  Kafka UI :8088
```

## Build & test

```bash
mvn -B test
# Testcontainers inventory IT requires Docker
```

## Run core services (example)

```powershell
$env:AGRICORE_DEV_MODE="true"
$env:GATEWAY_JWT_ENABLED="false"  # optional for seed scripts without JWT
mvn -pl services/identity-service spring-boot:run
# farm, crop-catalog, crop-cycle, work, harvest, inventory, traceability, gateway ...
```

## Happy path

```powershell
.\scripts\e2e-happy-path.ps1
```

## Seed

```powershell
.\scripts\seed-data.ps1
```

## Console and assistant

```powershell
Copy-Item .env.example .env
# Keep ASSISTANT_PROVIDER=none for a no-key smoke run.
docker compose up --build
```

- Console: `http://localhost:3000`
- Gateway: `http://localhost:8080`
- Assistant: internal Compose service `assistant-service:8093`; use `/api/v1/assistant/**` through the gateway.
- Traceability: `http://localhost:8092` for direct local verification; public browser calls use `/public/api` through the console edge.

Provider configuration is environment-only: `ASSISTANT_PROVIDER`, `ASSISTANT_PROVIDER_MODEL`, `ASSISTANT_PROVIDER_BASE_URL`, and `ASSISTANT_PROVIDER_API_KEY`. Never paste the key into Git, logs, Helm values, or a chat transcript. With the default `none` provider the API remains available and returns a safe limited/unavailable result. Tool calls are read-only farm reads, bounded by host/row/response limits, and carry the caller JWT.

## Assistant operational controls

| Situation | Action |
|---|---|
| Rotate provider key | Update the secret manager/Kubernetes Secret or local `.env`, restart only `assistant-service`, then check `/actuator/health`; never append the old/new key to evidence. |
| 429 budget response | Inspect the assistant service metric/log counters and wait for the configured `ASSISTANT_BUDGET_WINDOW`; do not increase limits during an incident without an owner-approved change. |
| `ASSISTANT_BUDGET_UNAVAILABLE` | Treat Redis as unavailable and restore Redis first. The assistant fails closed rather than admitting unbounded traffic. |
| SSE reconnect/replay | Reuse the generation ID and `Last-Event-ID`/sequence supported by the API; do not submit a second generation with a new idempotency key. |
| Retention/deletion | The worker purges archived conversations, audit events, and generation events according to `ASSISTANT_ARCHIVED_CONVERSATION_RETENTION`, `ASSISTANT_AUDIT_RETENTION`, and `ASSISTANT_GENERATION_EVENT_RETENTION`. Verify only counts/IDs in evidence. |
| Provider incident | Set `ASSISTANT_PROVIDER=none`, restart the assistant, and keep existing conversation data. Roll back by restoring the previous image tag and configuration after health/test gates pass. |

For cluster installs, create the external database Secret before `helm upgrade --install`; the chart's assistant database Job is idempotent and waits for Postgres. See [assistant database provisioning](./assistant-database-provisioning.md).
