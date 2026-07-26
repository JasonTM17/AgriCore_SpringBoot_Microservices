# identity-service

## Purpose

Owns users, roles, credentials, and token issuance. Every other service trusts it: it signs
RS256 access tokens and publishes the JWKS that domain services and the gateway use to verify
them locally. Calls no other AgriCore service; depends on PostgreSQL and Redis.

## API surface

- `POST /api/v1/auth/register` — create an account (gated by `AGRICORE_REGISTRATION_ENABLED`), assigns `FIELD_WORKER`
- `POST /api/v1/auth/login` — issue access + refresh tokens; rate limited, account lockout on repeated failure
- `POST /api/v1/auth/refresh` — rotate refresh token; reuse of a rotated token revokes the family
- `POST /api/v1/auth/logout` — revoke a refresh token
- `GET /api/v1/users/me` — current user profile
- `PATCH /api/v1/admin/users/{userId}/roles` — replace a user's roles (`SYSTEM_ADMIN` only)
- `GET /.well-known/jwks.json` — public keys for token verification
- Contract: `contracts/openapi/identity-service.v1.yaml`
- Events published: `UserRegistered.v1` → `agricore.identity.events` (transactional outbox)
- Events consumed: none

## Env vars

| Name | Required | Default | Description |
|------|----------|---------|-------------|
| `IDENTITY_PORT` | no | `8081` | HTTP listen port |
| `POSTGRES_HOST` | no | `localhost` | Database host |
| `POSTGRES_PORT` | no | `5434` | Database port (compose maps 5432 internally) |
| `POSTGRES_USER` | no | `agricore` | Database user |
| `POSTGRES_PASSWORD` | yes in prod | dev value | Database password |
| `REDIS_HOST` | no | `localhost` | Redis host for login rate limiting |
| `REDIS_PORT` | no | `6380` | Redis port |
| `JWT_ISSUER` | no | `https://agricore.local/identity` | `iss` claim and JWKS issuer |
| `AGRICORE_JWT_PRIVATE_KEY_PATH` | yes in prod | empty | RSA private key path; empty means ephemeral keys |
| `AGRICORE_JWT_PUBLIC_KEY_PATH` | yes in prod | empty | RSA public key path |
| `JWT_ACCESS_TOKEN_TTL_SECONDS` | no | `900` | Access token lifetime |
| `JWT_REFRESH_TOKEN_TTL_SECONDS` | no | `604800` | Refresh token lifetime |
| `AGRICORE_REGISTRATION_ENABLED` | no | `true` | Public self-registration; set `false` in production |
| `AGRICORE_RATE_LIMIT_FAIL_OPEN` | no | `false` | When Redis is down: `false` denies logins (fail-closed) |
| `AGRICORE_TRUST_FORWARDED_HEADERS` | no | `false` | Honour `X-Forwarded-For`; only behind a trusted proxy |
| `KAFKA_BOOTSTRAP_SERVERS` | no | `localhost:9092` | Broker for the outbox publisher |

## Run locally

```bash
# infrastructure first (Postgres 5434, Redis 6380, Kafka 9092)
./scripts/dev-up.sh          # PowerShell: .\scripts\dev-up.ps1

# stable signing keys (once)
.\scripts\generate-jwt-keys.ps1

mvn -pl services/identity-service spring-boot:run
```

## Test

```bash
./mvnw -B -pl services/identity-service -am test      # H2 + Flyway, no broker needed
./mvnw -B -pl services/identity-service -am verify    # adds the JaCoCo report
```

Covers the registration gate, fail-closed login rate limiting, refresh-token rotation, token
hashing, JWKS exposure, and the `UserRegistered.v1` outbox contract. Coverage is measured but
not yet gated; the target for this service is ≥ 90% lines / ≥ 85% branches as a critical module.

## Runbook

- **Rotate signing keys** — generate a new pair, mount as `AGRICORE_JWT_PRIVATE_KEY_PATH`, keep the
  previous public key served in JWKS until all issued access tokens expire (see `JWT_ACCESS_TOKEN_TTL_SECONDS`).
- **Unlock an account** — clear `failed_login_count` / `locked_until` on the `users` row, or wait out
  `lockout-duration-minutes`.
- **Redis down** — logins fail closed by default. Set `AGRICORE_RATE_LIMIT_FAIL_OPEN=true` only as a
  conscious availability trade-off.
- **Outbox backlog** — rows in `outbox_events` with `published_at IS NULL` mean the publisher cannot
  reach Kafka; check `last_error` and `publish_attempts`.
- **Disable registration** — `AGRICORE_REGISTRATION_ENABLED=false`, then create users via the admin role endpoint.
