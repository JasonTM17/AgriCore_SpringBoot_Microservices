# iot-service

## Purpose

Registers field devices and ingests sensor readings (soil moisture, temperature, and similar), keeping
the latest reading per device for operational dashboards. Called by the gateway and by devices through
it; calls no other AgriCore service.

## API surface

- `POST /api/v1/iot/devices` — register a device against a farm/plot
- `POST /api/v1/iot/readings` — ingest a sensor reading
- Contract: `contracts/openapi/iot-service.v1.yaml`
- Events published: none today. `SensorReadingReceived.v1`, `SensorThresholdExceeded.v1`, and
  `DeviceOfflineDetected.v1` exist as constants with no producer — threshold alerting is not implemented.
- Events consumed: none

## Env vars

| Name | Required | Default | Description |
|------|----------|---------|-------------|
| `IOT_PORT` | no | `8090` | HTTP listen port |
| `POSTGRES_HOST` | no | `localhost` | Database host |
| `POSTGRES_PORT` | no | `5434` | Database port |
| `POSTGRES_USER` | no | `agricore` | Database user |
| `POSTGRES_PASSWORD` | yes in prod | dev value | Database password |
| `JWT_ISSUER` | no | `https://agricore.local/identity` | Expected `iss` claim |
| `IDENTITY_JWKS_URI` | no | identity JWKS URL | Key source for local token verification |
| `AGRICORE_DEV_MODE` | no | `false` | Dev shortcuts; keep `false` outside local work |

Database: `agricore_iot`.

## Run locally

```bash
./scripts/dev-up.sh
mvn -pl services/iot-service spring-boot:run
```

## Test

```bash
./mvnw -B -pl services/iot-service -am test
./mvnw -B -pl services/iot-service -am verify   # adds the JaCoCo report
```

Target once coverage gating is enforced: ≥ 70% lines / ≥ 65% branches.

## Runbook

- **Readings rejected** — the device must be registered first; an unknown device id is refused rather
  than auto-created.
- **No alerting on bad readings** — expected: threshold evaluation and notification fan-out are not
  implemented. Do not document them as working.
- **High ingest volume** — readings are written synchronously to PostgreSQL; there is no buffering
  layer, so sustained high-rate ingest needs a design change, not a config tweak.
- **Reset local data** — drop and recreate `agricore_iot`, restart to replay migrations.
