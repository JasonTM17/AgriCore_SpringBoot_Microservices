# traceability-service

## Purpose

Builds the farm-to-consumer trace record and serves it publicly by QR code. Consumes harvest
completions into a read model; the public endpoint is the only unauthenticated surface in the
platform, so its response is deliberately narrow.

## API surface

- `GET /public/api/v1/traceability/{traceabilityCode}` — **public**, no auth: farm name, plot code,
  product name, harvest data. No internal ids, prices, or user data.
- `POST /api/v1/traceability/batches` — authenticated, role-gated write path for batch records
- Contract: `contracts/openapi/traceability-service.v1.yaml`
- Events published: none (`TraceabilityBatchCreated.v1` / `TraceabilityCodeGenerated.v1` are constants)
- Events consumed: `HarvestCompleted.v1` from `agricore.harvest.events`, idempotent via `processed_events`,
  poison messages routed to `agricore.harvest.events.DLT`

## Env vars

| Name | Required | Default | Description |
|------|----------|---------|-------------|
| `TRACEABILITY_PORT` | no | `8092` | HTTP listen port |
| `POSTGRES_HOST` | no | `localhost` | Database host |
| `POSTGRES_PORT` | no | `5434` | Database port |
| `POSTGRES_USER` | no | `agricore` | Database user |
| `POSTGRES_PASSWORD` | yes in prod | dev value | Database password |
| `KAFKA_BOOTSTRAP_SERVERS` | no | `localhost:9092` | Broker for the consumer and DLT producer |
| `JWT_ISSUER` | no | `https://agricore.local/identity` | Expected `iss` claim |
| `IDENTITY_JWKS_URI` | no | identity JWKS URL | Key source for local token verification |
| `AGRICORE_DEV_MODE` | no | `false` | Dev shortcuts; keep `false` outside local work |

Database: `agricore_traceability`.

## Run locally

```bash
./scripts/dev-up.sh
mvn -pl services/traceability-service spring-boot:run

# public read needs no token
curl http://localhost:8092/public/api/v1/traceability/<code>
```

## Test

```bash
./mvnw -B -pl services/traceability-service -am test
./mvnw -B -pl services/traceability-service -am verify   # adds the JaCoCo report
```

The public response body is asserted by `scripts/e2e-happy-path.ps1`, which saves it as
`traceability.json` in the evidence bundle.

## Runbook

- **QR code returns 404** — the read model is populated asynchronously from the harvest event; check
  `processed_events`, then the DLT. A missing code usually means the event never arrived.
- **Public endpoint leaking fields** — treat as a security incident: the public projection must expose
  only farm name, plot code, product, and harvest data. Never widen it to satisfy a UI.
- **Write endpoint returns 403** — the batch write path is role-gated; confirm the caller's role rather
  than relaxing the gate.
- **Replay DLT** — republish onto the source topic; the consumer dedupes on `eventId`.
