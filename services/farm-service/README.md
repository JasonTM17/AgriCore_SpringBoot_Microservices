# Farm Service

## Purpose

Owns enterprises, farms, areas, plots, soil profiles, irrigation zones, and
subject-to-farm memberships. It is the authoritative farm/plot scope service
for downstream applications and publishes farm/plot lifecycle events through a
transactional outbox.

## API and events

The [OpenAPI contract](../../contracts/openapi/farm-service.v1.yaml) covers:

- `/api/v1/enterprises` and `/api/v1/farms`;
- farm areas and plots;
- plot soil profiles and irrigation zones;
- farm memberships, including protected create/delete boundaries.

Published to `agricore.farm.events`: `FarmCreated.v1`, `PlotCreated.v1`, and
`PlotStatusChanged.v1`. The creator receives the initial farm membership;
membership uniqueness and last-membership rules are database-backed.

## Configuration

Database: `agricore_farm`. Core variables are `FARM_PORT`, `POSTGRES_*`,
`KAFKA_BOOTSTRAP_SERVERS`, `IDENTITY_JWKS_URI`, `JWT_ISSUER`, and
`AGRICORE_DEV_MODE`.

## Run and verify

```bash
./mvnw -B -pl services/farm-service -am test
./mvnw -pl services/farm-service spring-boot:run
```

- A missing or inaccessible plot is intentionally masked as `404`.
- Event backlog: inspect unpublished `outbox_events` and `last_error`.
- Never repair downstream farm scope by cross-service SQL; use the owning API
  or an explicitly reviewed data migration.

See the [authorization model](../../docs/security/microservices-authz.md) and
[AsyncAPI](../../contracts/asyncapi/agricore-events.yaml).
