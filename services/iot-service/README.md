# IoT Service

## Purpose

Owns farm-scoped devices, TimescaleDB-backed readings, versioned threshold
rules, alert cooldown, and offline detection. HTTP and authenticated MQTT QoS 1
ingestion share the same bounded, idempotent application path.

## API and events

- `/api/v1/iot/devices`: register and list devices.
- `/api/v1/iot/readings`: ingest and query readings/operational state as defined
  by the [OpenAPI contract](../../contracts/openapi/iot-service.v1.yaml).
- MQTT topic: `agricore/telemetry/{deviceCode}/reading`; stable `readingId`
  deduplicates QoS 1 redelivery and rejects conflicting ID reuse.

Published through the outbox: `SensorReadingReceived.v1`,
`SensorThresholdExceeded.v1`, and `DeviceOfflineDetected.v1`.
Notification consumes threshold/offline events. See
[event AsyncAPI](../../contracts/asyncapi/agricore-events.yaml) and
[MQTT AsyncAPI](../../contracts/asyncapi/agricore-mqtt.yaml).

## Configuration

Database: `agricore_iot`; TimescaleDB migration creates the reading hypertable.
Core groups are `IOT_MQTT_*`, `IOT_ALERT_COOLDOWN_MINUTES`,
`IOT_OFFLINE_*`, `POSTGRES_*`, `KAFKA_BOOTSTRAP_SERVERS`,
`FARM_SERVICE_URL`, `IDENTITY_JWKS_URI`, and `JWT_ISSUER`.
The repository selects no telemetry deletion policy.

## Run and verify

```bash
./mvnw -B -pl services/iot-service -am test
./mvnw -pl services/iot-service spring-boot:run
```

- Register the mapped device code before HTTP or MQTT ingestion.
- Rate-limited device records are acknowledged and counted; global queue
  saturation disconnects so broker redelivery can apply backpressure.
- Production requires MQTT TLS, managed device credentials, and per-device
  broker ACLs.

See the [IoT ADR](../../docs/adr/0015-authenticated-mqtt-iot-ingestion.md) and
[local simulator runbook](../../docs/runbooks/local-operations.md#mqtt-telemetry-smoke-test).
