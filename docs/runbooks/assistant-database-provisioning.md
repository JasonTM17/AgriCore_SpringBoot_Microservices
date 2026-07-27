# Assistant Database Provisioning

Fresh PostgreSQL volumes create `agricore_assistant` from `init-databases.sql`. Existing named volumes do not rerun Docker entrypoint initialization, so provision the database once before starting `assistant-service`.

## Existing volume

Bash:

```bash
docker compose -f docker-compose.infrastructure.yml exec -T postgres \
  psql -v ON_ERROR_STOP=1 -U agricore -d postgres \
  < infrastructure/docker/postgres/provision-assistant-database.sql
```

PowerShell:

```powershell
Get-Content -Raw infrastructure/docker/postgres/provision-assistant-database.sql |
  docker compose -f docker-compose.infrastructure.yml exec -T postgres `
    psql -v ON_ERROR_STOP=1 -U agricore -d postgres
```

The script is idempotent. Replace `agricore` when `POSTGRES_USER` is customized.

Verify:

```bash
docker compose -f docker-compose.infrastructure.yml exec postgres \
  psql -U agricore -d postgres -tAc \
  "SELECT 1 FROM pg_database WHERE datname = 'agricore_assistant'"
```

Expected output: `1`. Flyway applies the assistant schema when the service starts.

For the full Compose start order and observability checks, see [local operations](./local-operations.md).
