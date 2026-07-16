# JWT + Gateway E2E proof (local)

Date: 2026-07-16

Infrastructure: Postgres `:5434`, Redis `:6380`, Kafka `:9092` (docker compose infrastructure).

Runtime: identity `:8081`, farm `:8082`, api-gateway `:8080` as local `java -jar` processes with `AGRICORE_DEV_MODE=false`.

## Results

| Check | Result |
|-------|--------|
| Unsigned forged JWT (`alg=none`) against farm `/api/v1/farms` | **401** |
| Real RS256 access token (FARM_MANAGER) via gateway `POST /api/v1/farms` | **201** farm created (`code=FARM-OK-*`) |
| Full monorepo `mvnw test` after security cutover | **BUILD SUCCESS** (all modules) |

## Notes

- Domain services validate JWT signature + issuer via identity JWKS (`libs/common-security`).
- `X-Dev-*` headers only work when `agricore.security.dev-mode=true` (tests/local only).
- Default self-registration role is `FIELD_WORKER`; farm create requires `FARM_MANAGER` or `SYSTEM_ADMIN`.
