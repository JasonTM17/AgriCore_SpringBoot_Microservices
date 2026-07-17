# Microservices authorization model

## Boundaries

- Each service validates JWT independently via identity JWKS (`libs/common-security`).
- `AGRICORE_DEV_MODE=false` in compose/Helm production-like profiles.
- X-Dev headers are accepted **only** when `agricore.security.dev-mode=true` (local tests).
- Unsigned JWT payloads are never trusted.

## Service-to-service

- Sales → inventory forwards the caller's Bearer token (`JwtAuthenticationToken`).
- No shared database credentials across business domains beyond the single Postgres *instance* hosting **separate databases** per service.

## Public surface

- `/public/api/v1/traceability/{code}` is open and returns only farm/plot/product-safe fields.
- Identity `/api/v1/auth/**` and `/.well-known/jwks.json` are open for bootstrap.

## Roles (samples)

`SYSTEM_ADMIN`, `FARM_MANAGER`, `AGRONOMIST`, `FIELD_WORKER`, `WAREHOUSE_MANAGER`, `SALES_STAFF`, `AUDITOR`.
