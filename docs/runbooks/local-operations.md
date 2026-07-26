# Local Operations Runbook

## Prerequisites

- JDK 21 and the included Maven wrapper.
- Node.js 22.13.0+ and pnpm 11+ for console work.
- Docker with Docker Compose.
- OpenSSL for local JWT key generation.

Create local configuration and JWT keys once:

```powershell
Copy-Item .env.example .env
.\scripts\generate-jwt-keys.ps1
```

Never commit `.env`, generated private keys, or provider credentials.

## Start the full local stack

The observability Compose file joins the external `agricore_default` network. Create that network by starting the core infrastructure first, then start observability, then the applications:

```powershell
docker compose up -d postgres redis kafka kafka-ui kafka-topics-init mqtt minio
docker compose -f docker-compose.observability.yml up -d
docker compose up -d --build
```

| Component | Local endpoint |
|---|---|
| Console | `http://localhost:3000` |
| API and JWKS through the same-origin edge | `http://localhost:3000` |
| Kafka UI | `http://localhost:8088` |
| Mailpit captured email | `http://localhost:8025` |
| MQTT broker | `tcp://localhost:1883` |
| MinIO API | `http://localhost:9000` |
| MinIO console | `http://localhost:9001` |
| Grafana | `http://localhost:3001` |
| Prometheus | `http://localhost:9090` |
| Prometheus targets | `http://localhost:9090/targets` |
| Tempo readiness/API | `http://localhost:3200/ready` |
| Tempo OTLP/HTTP receiver | `http://localhost:4318/v1/traces` |

The local Grafana credentials come from `GRAFANA_ADMIN_USER` and `GRAFANA_ADMIN_PASSWORD` in `.env`: the template uses `admin` and a development-only placeholder. MinIO credentials come from `MINIO_ROOT_USER` and `MINIO_ROOT_PASSWORD`; replace them outside local development.

### MQTT telemetry smoke test

Register the mapped device code(s) through `POST /api/v1/iot/devices`, then publish deterministic QoS 1 readings:

```powershell
./scripts/sensor-simulator.ps1 -DeviceCount 1 -Iterations 1 -FrequencySeconds 0 -Seed 42
```

The topic is `agricore/telemetry/{deviceCode}/reading`. Payload `readingId` is required and provides durable deduplication. Local service credentials come from `MQTT_SERVICE_*`; unique simulator passwords are derived per device from the development-only `MQTT_DEVICE_PASSWORD_SEED`. The broker rejects anonymous clients and ACLs device users to their own topic. Before work enters the shared processing queue, the listener enforces a per-device token bucket (`IOT_MQTT_RATE_PER_SECOND`, `IOT_MQTT_BURST_CAPACITY`) and in-flight cap (`IOT_MQTT_MAX_IN_FLIGHT_PER_DEVICE`). `IOT_MQTT_TRACKED_DEVICE_CAPACITY` and `IOT_MQTT_DEVICE_IDLE_TTL` bound admission state. Rate-limited QoS 1 records are acknowledged and counted as `rate_limited`; shared-queue saturation disconnects so the broker can redeliver. The local listener uses plaintext only because it is loopback-bound and `IOT_MQTT_ALLOW_INSECURE=true`. Production deployment must use externally provisioned per-device credentials, an authenticated TLS broker with per-device ACLs, and Kubernetes Secret references.

The simulator supports device count, frequency, normal/anomaly ranges, anomaly probability, plot mapping, and deterministic seed. For POSIX:

```sh
MQTT_DEVICE_COUNT=3 MQTT_DEVICE_USERS='SIM-001,SIM-002,SIM-003' MQTT_ITERATIONS=10 MQTT_INTERVAL_SECONDS=2 MQTT_PLOT_MAP='SIM-001=PLOT-1,SIM-002=PLOT-2,SIM-003=PLOT-3' \
  docker compose -f docker-compose.yml -f docker-compose.mqtt-simulator.yml --profile simulator run --rm iot-mqtt-simulator
```

## Verify startup

```powershell
docker compose ps
docker compose -f docker-compose.observability.yml ps

Invoke-RestMethod http://localhost:3000/healthz
docker inspect --format '{{json .State.Health.Status}}' agricore-gateway
Invoke-RestMethod http://localhost:9090/-/ready
Invoke-RestMethod http://localhost:3200/ready
Invoke-RestMethod http://localhost:8025/readyz
Invoke-RestMethod http://localhost:9000/minio/health/live
```

Open Prometheus targets and confirm all 13 application jobs are `UP`. Gateway
and assistant-service are scraped on the shared Compose network; the other 11
applications use development-published ports through the host bridge.

Grafana provisions these dashboards in the read-only `AgriCore` folder:

1. AgriCore Platform Overview
2. AgriCore Service Health
3. AgriCore Database Health
4. AgriCore Kafka Health
5. AgriCore Inventory Operations
6. AgriCore IoT Ingestion
7. AgriCore Business Metrics

Dashboard and datasource edits must be made in `infrastructure/monitoring/grafana/` and applied by restarting Grafana; UI edits are disabled by provisioning.

## Verify traces

Generate a gateway request, then query Tempo for recent gateway traces:

```powershell
Invoke-RestMethod http://localhost:3000/.well-known/jwks.json | Out-Null

$query = [uri]::EscapeDataString('{ resource.service.name = "api-gateway" }')
$result = Invoke-RestMethod "http://localhost:3200/api/search?q=$query&limit=5"
if (-not $result.traces) {
  throw "No api-gateway traces found in Tempo"
}
$result.traces
```

Local Compose sends OTLP/HTTP traces to `http://tempo:4318/v1/traces` with sampling probability `1.0`. Helm defaults to sampling probability `0.1`, but the endpoint is empty and export is disabled until an operator sets `observability.otlpTracingEndpoint`.

Tempo local retention is configured for 48 hours. Its container storage is not persistent, so container removal can discard traces before that limit. Loki data is persistent in the `agricore_loki_data` volume; MinIO data is persistent in `agricore_minio_data`.

## Verify ECS JSON logs

Compose enables ECS structured console logging for all 13 Spring applications:

```powershell
$entry = docker logs agricore-gateway --tail 100 2>&1 |
  Where-Object { $_ -match '^\{' } |
  Select-Object -Last 1 |
  ConvertFrom-Json

$entry.ecs.version
$entry.service.name
$entry.service.environment
```

After a traced request, entries emitted inside that request may also include `trace.id` and `span.id`. Alloy discovers only this Compose project, enriches service labels, and forwards container stdout to Loki. Query recent logs through Loki's internal API or the Grafana Explore view:

```powershell
$query = [uri]::EscapeDataString('{service_name="agricore-minio"}')
$user = if ($env:GRAFANA_ADMIN_USER) { $env:GRAFANA_ADMIN_USER } else { 'admin' }
$password = if ($env:GRAFANA_ADMIN_PASSWORD) { $env:GRAFANA_ADMIN_PASSWORD } else { 'agricore_grafana_dev_change_me' }
$basic = [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes("$user`:$password"))
$logs = Invoke-RestMethod -Headers @{ Authorization = "Basic $basic" } "http://localhost:3001/api/datasources/proxy/uid/loki/loki/api/v1/query_range?query=$query&limit=20"
if (-not $logs.data.result) { throw "No MinIO logs found in Loki" }
$logs.data.result
```

Loki local retention is 72 hours and Docker's `json-file` logs are bounded to three 10 MiB files per container by default. The Docker socket is mounted read-only by Alloy for discovery; treat local Docker access as a host-level trust boundary. A local `spring-boot:run` process emits ECS JSON only when the corresponding structured logging environment or properties are set, and is not collected by Alloy unless it is containerized.

## Custom Prometheus metrics

| Metric family | Labels/notes |
|---|---|
| `agricore_outbox_backlog` | Gauge for unpublished transactional outbox rows across event-producing services |
| `agricore_kafka_dlq_attempts_total` | `consumer=inventory-service|traceability-service|notification-service`; DLT recovery attempts, not topic depth or confirmed success |
| `agricore_harvest_processing_seconds_*` | `outcome=success|failure`; timer/histogram series |
| `agricore_inventory_reservations_total` | `outcome=success|insufficient_stock` |
| `agricore_inventory_harvest_events_total` | `outcome=applied|duplicate` |
| `agricore_iot_readings_total` | Accepted readings |
| `agricore_iot_alerts_total` | `outcome=created|suppressed` |
| `agricore_iot_open_alerts` | Current open alerts gauge |
| `agricore_sales_sagas_total` | Terminal saga outcome |
| `agricore_notification_deliveries_total` | `outcome=sent|failed|duplicate`; notification delivery outcomes |
| `agricore_assistant_generations_total` | `outcome=completed|failed|cancelled` |
| `agricore_assistant_retention_purged_total` | `dataset=generation_events|conversations|audit_events`; physically deleted expired records |
| `agricore_assistant_retention_cleanup_failures_total` | Retention cleanup failures |

Example Prometheus API query:

```powershell
$metric = [uri]::EscapeDataString('agricore_outbox_backlog')
Invoke-RestMethod "http://localhost:9090/api/v1/query?query=$metric"
```

## Build, test, and exercise the platform

```powershell
.\mvnw.cmd -B verify
pnpm install --frozen-lockfile
pnpm contracts:check
pnpm lint
pnpm typecheck
pnpm test
pnpm build

.\scripts\e2e-happy-path.ps1
.\scripts\seed-data.ps1
```

### Deterministic development dataset

The seed tool is deterministic and reuses farms/plots by code. Choose a bounded
profile before running it:

| Profile | Farms | Plots | Production flows | Tasks | Readings | Sales orders |
|---|---:|---:|---:|---:|---:|---:|
| `Smoke` / `Quick` | 2 | 6 | 1 | 1 | 2 | 1 |
| `Demo` / `Showcase` (default) | 8 | 48 | 4 | 8 | 20 | 4 |
| `Large` | 32 | 768 | 32 | 128 | 640 | 16 |

```powershell
$env:AGRICORE_SEED_PASSWORD = "<local-only-password>"
.\scripts\seed-data.ps1 -Profile Large -DryRun
.\scripts\seed-data.ps1 -Profile Large
Remove-Item Env:AGRICORE_SEED_PASSWORD
```

`FarmLimit`, `PlotsPerFarm`, `DomainFarmLimit`, `TasksPerFarm`,
`ReadingsPerDevice`, and `SalesOrderLimit` may override a profile within their
hard caps. The script checks C: and the repository drive before work and after
every 100 synchronized calls, stops below `MinimumFreeSpaceGb` (2 GB by
default), and throttles larger runs. `DryRun` performs no API or database calls.

The script registers seven local personas, grants their roles through the
Compose identity database, then drives authenticated farm, crop-cycle, work,
attachment, warehouse, harvest, IoT, sales, notification, traceability, and
assistant contracts. Harvest projection and notification arrival are verified
before completion. Direct database writes are limited to local role bootstrap;
read-only SQL fills idempotency gaps where a service has no list endpoint and
produces authoritative final counts. Work evidence uses the checksummed
repository WebP files and is uploaded idempotently to MinIO.

The password is read from `AGRICORE_SEED_PASSWORD`, never stored or printed.
Keep the same local value for repeat runs against an existing volume; changing
it does not silently reset existing accounts. `seed-data.sh` delegates to the
same implementation and requires PowerShell 7 (`pwsh`).

### Public traceability read smoke profile

Use a real code from the persisted Traceability projection:

```powershell
$env:TRACEABILITY_CODE = "<persisted-code>"
$env:VUS = "10"
$env:DURATION = "15s"
k6 run .\scripts\load\public-traceability-read-smoke.js
Remove-Item Env:TRACEABILITY_CODE, Env:VUS, Env:DURATION
```

The default thresholds require more than 99% successful checks, less than 1%
HTTP failures, p95 below 750 ms, and p99 below 1,500 ms. Override
`P95_MS`, `P99_MS`, or `PACING_SECONDS` only for an explicitly documented
environment target.

## Console and assistant

- Assistant is internal at `assistant-service:8093`; use `/api/v1/assistant/**` through the gateway.
- Traceability is host-published at `http://localhost:8092`; public browser requests use `/public/api` through the console edge.
- Provider settings are `ASSISTANT_PROVIDER`, `ASSISTANT_PROVIDER_MODEL`, `ASSISTANT_PROVIDER_BASE_URL`, and `ASSISTANT_PROVIDER_API_KEY`.
- Provider `none` keeps the API available with a safe limited/unavailable outcome.
- Tool calls are read-only farm reads, carry the caller JWT, and enforce host, row, response-size, and timeout bounds.
- Archived conversations, audit events, and generation replay events carry
  explicit expiry timestamps. Defaults are `P90D`, `P365D`, and `PT24H`;
  `ASSISTANT_RETENTION_CLEANUP_*` controls a bounded cleanup job.

Retention cleanup order is generation events, archived conversations, then
audit events, each capped by the configured batch size per run. Monitor
`agricore_assistant_retention_purged_total` and
`agricore_assistant_retention_cleanup_failures_total`. Set
`ASSISTANT_RETENTION_CLEANUP_ENABLED=false` only for a controlled maintenance
window; expired rows otherwise remain physically stored.

## Notification delivery

- Compose routes email to `mailpit:1025`; only the Mailpit UI is host-published on port `8025`.
- The direct notification endpoint requires `SYSTEM_ADMIN`. An optional `idempotencyKey` prevents repeated delivery and returns `409` if reused for different content.
- External email/SMS uses at-most-once automatic delivery. An adapter result is
  persisted after one provider attempt; a stale `DELIVERING` lease becomes
  `FAILED` with `DELIVERY_OUTCOME_UNKNOWN` instead of being resent because the
  provider may already have accepted it.
- `IN_APP` delivery is an idempotent local write keyed by notification ID. Its
  `REQUESTED` or stale `DELIVERING` rows may be reclaimed within
  `NOTIFICATION_DELIVERY_MAX_ATTEMPTS`; unsupported or exhausted delivery ends
  in `FAILED`.
- Production SMTP host, TLS policy, and credentials are deployment inputs. Helm reads username/password from the Secret configured by `notification.smtp.credentialSecretName`.

| Situation | Action |
|---|---|
| Rotate provider key | Update the secret source, restart only assistant-service, then check `/actuator/health`. Do not record either key in evidence. |
| `429` budget response | Wait for `ASSISTANT_BUDGET_WINDOW`; change limits only through an approved configuration change. |
| `ASSISTANT_BUDGET_UNAVAILABLE` | Restore Redis first. The assistant fails closed. |
| SSE reconnect/replay | Reuse the generation ID and `Last-Event-ID`; do not submit a second generation with a new idempotency key. |
| Provider incident | Set `ASSISTANT_PROVIDER=none`, restart assistant-service, and preserve conversation data. |

For an existing PostgreSQL volume, follow [assistant database provisioning](./assistant-database-provisioning.md). For cluster installs, create the external database Secret before `helm upgrade --install`.

### Inventory warehouse farm-scope upgrade

Inventory migration `V4__add_warehouse_farm_scope.sql` adds `warehouses.farm_id`. It intentionally does not guess a farm for existing warehouses. New warehouse requests require `farmId`; an existing warehouse with a null farm assignment is unavailable to public inventory reads/mutations and Work material consumption until an operator maps it to the correct farm. Migration `V5__scope_processed_events_to_farms.sql` applies the same fail-closed rule to legacy harvest acknowledgement markers.

Audit the inventory database after migration:

```sql
SELECT id, code, name
FROM warehouses
WHERE farm_id IS NULL
ORDER BY code;
```

For each row, verify the farm in farm-service before applying an explicit mapping:

```sql
UPDATE warehouses
SET farm_id = '<verified-farm-uuid>'
WHERE id = '<warehouse-uuid>'
  AND farm_id IS NULL;
```

Do not bulk-assign a default farm. Re-run the audit and require zero rows before enabling Work material stock-out for migrated warehouses.

### Inventory expiry-aware batches

Migration `V6__add_expiry_aware_inventory_batches.sql` creates the lot ledger and
backfills each existing item into a `LEGACY-<item-uuid>` lot with no expiry. New
stock-in requests may provide `lotCode` and `expiresAt`; when omitted, the service
derives a deterministic lot code from the movement reference. Reservations and
dispatch use FEFO/FIFO ordering and reject expired available stock.

After applying V6, reconcile aggregate and lot balances before enabling production
traffic. The following query should return no rows (the aggregate is the sum of
batch quantities and the reserved balance is the sum of batch reservations):

```sql
SELECT i.id,
       i.on_hand_quantity,
       COALESCE(SUM(b.quantity), 0) AS batch_quantity,
       i.reserved_quantity,
       COALESCE(SUM(b.reserved_quantity), 0) AS batch_reserved_quantity
FROM inventory_items i
LEFT JOIN inventory_batches b ON b.inventory_item_id = i.id
GROUP BY i.id, i.on_hand_quantity, i.reserved_quantity
HAVING i.on_hand_quantity <> COALESCE(SUM(b.quantity), 0)
    OR i.reserved_quantity <> COALESCE(SUM(b.reserved_quantity), 0);
```

Do not manually delete a batch with reservation allocations. Repair through the
inventory application flow so the aggregate, batch ledger, movement, and outbox
remain consistent.

## Stop the stack

Stop observability before the main project so its containers release the external network:

```powershell
docker compose -f docker-compose.observability.yml down
docker compose down
```

Named PostgreSQL, MinIO, and Loki data remains. Add `--volumes` only when intentionally deleting local data.
