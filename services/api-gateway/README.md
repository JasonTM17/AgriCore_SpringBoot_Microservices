# API Gateway

## Purpose

Spring Cloud Gateway is the authenticated API edge for the 12 internal domain
applications. It validates RS256 access tokens against Identity JWKS, checks
issuer and `agricore-api` audience, preserves the bearer token, and owns no
database. Browser traffic normally reaches it through the Console Nginx
same-origin edge.

## Routes

| Prefix | Upstream |
|---|---|
| `/api/v1/auth/**`, `/api/v1/users/**`, `/api/v1/admin/**`, `/.well-known/jwks.json` | Identity |
| `/api/v1/assistant/**` | Assistant |
| `/api/v1/enterprises/**`, `/api/v1/farms/**`, `/api/v1/plots/**` | Farm |
| `/api/v1/crops/**`, `/api/v1/crop-cycles/**`, `/api/v1/work-tasks/**` | Catalog, Crop Cycle, Work |
| `/api/v1/harvests/**`, `/api/v1/inventory/**`, `/api/v1/sales/**` | Harvest, Inventory, Sales |
| `/api/v1/iot/**`, `/api/v1/notifications/**` | IoT, Notification |
| `/api/v1/traceability/**`, `/public/api/v1/traceability/**` | Traceability |

The authoritative route table is
[`application.yml`](src/main/resources/application.yml). The public
traceability and JWKS paths are allowlisted; other application routes require a
valid access token.

## Configuration

Core variables are `GATEWAY_PORT`, `IDENTITY_JWKS_URI`, `JWT_ISSUER`,
`JWT_AUDIENCE`, `ASSISTANT_SERVICE_URL`, and one `*_SERVICE_URL` per upstream.
`ASSISTANT_RESPONSE_TIMEOUT` is separate because fetch-SSE is long lived.
Compose keeps the Gateway internal; use `http://localhost:3000` through the
Console edge.

## Run and verify

```bash
./mvnw -B -pl services/api-gateway -am test
./mvnw -pl services/api-gateway spring-boot:run
```

- Repeated `401`: verify issuer, audience, and JWKS reachability.
- Upstream `503`: check the target service health and configured base URL.
- Truncated Assistant stream: check both Gateway and Nginx SSE timeouts and
  buffering.

See the [Gateway contract](../../contracts/openapi/api-gateway.v1.yaml) and
[same-origin edge ADR](../../docs/adr/0014-api-gateway-and-same-origin-edge.md).
