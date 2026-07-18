# 08 — Chẩn đoán IoT

- Stitch screen: `190db415587b46c99f821508dc036874`
- Device: desktop, `2560 × 2048` export
- Primary roles: `FARM_MANAGER`, `AGRONOMIST`, `FIELD_WORKER`

## Intent

Provide a request/response diagnostic flow for device registration and reading ingestion instead of an unsupported monitoring dashboard.

## Contract anchors

- Device form maps to `RegisterDeviceRequest`: `deviceCode`, `plotId`, and `name`.
- Reading form maps to `IngestReadingRequest`: `deviceCode`, `metricType`, `metricValue`, `unit`, and `recordedAt`.
- Result card maps exactly to `IngestResultResponse`.
- During the configured 15-minute cooldown, the backend may return `alertRaised=false`, reuse an existing `alertId`, keep `alertStatus=OPEN`, and return `Alert suppressed by cooldown window`.
- The page must not claim device, reading, or alert history: no GET/list endpoints exist.

## Required states

Device registered, reading accepted without alert, new alert opened, alert suppressed by cooldown, unknown device, and validation failure. Session-log rows are client-local and must be labeled as such.

## Responsive behavior

Stack the three numbered stages in order on tablet/mobile and keep the response immediately after its submit action.
