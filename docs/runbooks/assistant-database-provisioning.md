# Assistant database provisioning

## Normal Compose startup

Do not provision the assistant database by hand during a normal local startup.
The root `docker-compose.yml` includes the infrastructure stack and starts the
one-shot `assistant-database-init` service after PostgreSQL is healthy. That
service runs the idempotent
`infrastructure/docker/postgres/provision-assistant-database.sql` script;
`assistant-service` waits for it to complete successfully before it starts.

```bash
docker compose up -d
```

`init-databases.sql` initializes a fresh PostgreSQL volume, while
`assistant-database-init` covers both fresh and existing named volumes. Flyway
then applies the assistant schema when `assistant-service` starts.

## Troubleshooting or recovery only

Use this section only when the one-shot initializer failed or an existing local
volume must be repaired. Keep the root Compose project context: run `docker
compose ...` from the repository root. Do **not** execute `docker compose -f
docker-compose.infrastructure.yml ...`; it can target a different project and
containers than the root stack.

1. Start PostgreSQL in the normal project and inspect the initializer result:

   ```bash
   docker compose up -d postgres assistant-database-init
   docker compose ps assistant-database-init
   ```

2. Reapply the idempotent script through the running PostgreSQL container.
   Replace `agricore` if `POSTGRES_USER` is customized.

   Bash:

   ```bash
   docker compose exec -T postgres \
     psql -v ON_ERROR_STOP=1 -U agricore -d postgres \
     < infrastructure/docker/postgres/provision-assistant-database.sql
   ```

   PowerShell:

   ```powershell
   Get-Content -Raw infrastructure/docker/postgres/provision-assistant-database.sql |
     docker compose exec -T postgres `
       psql -v ON_ERROR_STOP=1 -U agricore -d postgres
   ```

3. Verify the database, then start the dependent service:

   ```bash
   docker compose exec -T postgres \
     psql -U agricore -d postgres -tAc \
     "SELECT 1 FROM pg_database WHERE datname = 'agricore_assistant'"
   docker compose up -d assistant-service
   ```

Expected query output: `1`. If it is absent or the script fails, keep
`assistant-service` stopped and investigate PostgreSQL credentials/volume health
before retrying. For the full Compose start order and observability checks, see
[local operations](./local-operations.md).
