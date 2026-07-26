# api-gateway

## Purpose

Single edge entry point (Spring Cloud Gateway). Validates RS256 access tokens against
identity's JWKS, then routes to the eleven domain services over the internal network. Called by
clients; calls every domain service. Owns no database.

## API surface

The gateway defines no endpoints of its own — it forwards path prefixes to upstreams:

| Prefix | Upstream |
|--------|----------|
| `/api/v1/auth/**`, `/api/v1/users/**`, `/api/v1/admin/**`, `/.well-known/jwks.json` | identity-service |
| `/api/v1/farms/**`, `/api/v1/plots/**` | farm-service |
| `/api/v1/crops/**` | crop-catalog-service |
| `/api/v1/crop-cycles/**` | crop-cycle-service |
| `/api/v1/work-tasks/**` | work-service |
| `/api/v1/inventory/**` | inventory-service |
| `/api/v1/harvests/**` | harvest-service |
| `/api/v1/notifications/**` | notification-service |
| `/api/v1/iot/**` | iot-service |
| `/api/v1/sales/**` | sales-service |
| `/api/v1/traceability/**`, `/public/api/v1/traceability/**` | traceability-service |

- Contract: `contracts/openapi/api-gateway.v1.yaml`
- Events: none

Authoritative route list: `services/api-gateway/src/main/resources/application.yml`.

## Env vars

| Name | Required | Default | Description |
|------|----------|---------|-------------|
| `GATEWAY_PORT` | no | `8080` | HTTP listen port |
| `JWT_ISSUER` | no | `https://agricore.local/identity` | Expected `iss` claim |
| `IDENTITY_JWKS_URI` | no | `http://localhost:8081/.well-known/jwks.json` | JWKS endpoint for local verification |
| `AGRICORE_DEV_MODE` | no | `false` | Dev shortcuts; must stay `false` outside local work |
| `IDENTITY_SERVICE_URL` … `TRACEABILITY_SERVICE_URL` | no | localhost ports | Upstream base URLs, one per domain service |

Compose and Helm inject the upstream URLs; see `docker-compose.yml` and
`infrastructure/helm/agricore/values.yaml`.

## Run locally

```bash
./scripts/dev-up.sh
mvn -pl services/identity-service spring-boot:run   # gateway needs JWKS
mvn -pl services/api-gateway spring-boot:run
# http://localhost:8080
```

## Test

```bash
./mvnw -B -pl services/api-gateway -am test
```

Route and JWT-audience behavior is exercised end-to-end by `scripts/e2e-happy-path.ps1` against
the full compose stack rather than by unit tests in this module.

## Runbook

- **401 on every request** — JWKS unreachable or `JWT_ISSUER` mismatch; curl the JWKS URI from
  inside the gateway container.
- **404 on a valid path** — the route prefix is missing from `application.yml`; the gateway does
  not discover services dynamically (no registry, ADR-0002).
- **Upstream 503** — check the target container's `/actuator/health`; compose gates startup on healthchecks.
- **Add a service** — add the route to `application.yml`, the `*_SERVICE_URL` env in compose and
  Helm values, and the prefix table above.
