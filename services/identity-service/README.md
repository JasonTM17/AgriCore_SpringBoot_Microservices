# Identity Service

## Purpose

Owns users, roles, permission grants, credentials, refresh-token families, and
RS256 access-token issuance. It publishes JWKS for local verification by the
Gateway and domain services. Registration writes `UserRegistered.v1` to the
Identity transactional outbox in the same database transaction as the user.

## API and event

The [OpenAPI contract](../../contracts/openapi/identity-service.v1.yaml)
includes:

- register, login, refresh, and logout for token-body clients;
- `/api/v1/auth/web/**` cookie-based browser auth;
- current-user and administrator user/role operations;
- permission catalog and atomic role-permission replacement;
- public `/.well-known/jwks.json`.

`UserRegistered.v1` is published to `agricore.identity.events`. Its bounded
payload supports Notification's welcome email and excludes password hashes,
tokens, and credentials. See
[AsyncAPI](../../contracts/asyncapi/agricore-events.yaml).

## Configuration

Database: `agricore_identity`; Redis backs login rate limiting. Core variables
are `IDENTITY_PORT`, `POSTGRES_*`, `REDIS_*`, `AGRICORE_JWT_*_KEY_PATH`,
`JWT_*_TTL_SECONDS`, `AGRICORE_REGISTRATION_ENABLED`,
`AGRICORE_RATE_LIMIT_FAIL_OPEN`, and `KAFKA_BOOTSTRAP_SERVERS`.

## Run and verify

```bash
./mvnw -B -pl services/identity-service -am test
./mvnw -pl services/identity-service spring-boot:run
```

- Redis failure denies login by default; enable fail-open only as an explicit
  availability decision.
- Refresh-token reuse revokes the token family.
- Welcome email missing: inspect Identity unpublished `outbox_events`, then the
  Notification consumer/DLT and processed-event ledger.

See [JWT/JWKS ADR](../../docs/adr/0003-jwt-rs256-jwks.md) and
[authorization model](../../docs/security/microservices-authz.md).
