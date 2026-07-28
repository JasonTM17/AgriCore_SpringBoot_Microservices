# Developer workflows

Run commands from the repository root. Start with the narrowest command that
exercises the changed boundary, then run the broader checks required by shared
or public contracts.

## Console-only change

For `apps/agricore-console/` changes, install workspace dependencies when they
are not already present, then test the Console before broadening verification.

```bash
pnpm install --frozen-lockfile
pnpm test
pnpm lint
pnpm typecheck
pnpm build
```

Run `pnpm e2e` when the change affects an authenticated, browser, or SSE user
journey. See the [local operations runbook](runbooks/local-operations.md) for
the stack needed by browser tests.

## One Java service

For a change scoped to one module under `services/`, replace `<service>` with
its directory name and run its reactor slice first:

```bash
./mvnw -pl services/<service> -am test
```

Run the full reactor when a shared library, migration, cross-service contract,
or event behavior changes:

```bash
./mvnw -B verify
```

## Public contract or event-schema change

For an [OpenAPI](../contracts/openapi/) change consumed by the Console,
regenerate and check the tracked Console clients:

```bash
pnpm contracts:generate
pnpm contracts:check
```

`contracts:check` regenerates the tracked Console clients and fails on drift.
Follow it with the affected service test, then `pnpm typecheck` and
`pnpm build`; use `./mvnw -B verify` when the change affects a shared or
cross-service contract. For [AsyncAPI](../contracts/asyncapi/) or
[event schemas](../contracts/event-schemas/), run the affected producer and
consumer tests, then broaden to the full Maven reactor when the event crosses
service boundaries.

## Full-stack change

For a change spanning services, the Console, runtime configuration, or event
delivery, complete [Quick start](../README.md#quick-start), start the local
stack, then run the focused acceptance flow:

```powershell
docker compose up -d
pwsh scripts/e2e-happy-path.ps1
```

Broaden from the affected service or contract checks above to the full Maven
reactor and Console checks. For Compose, Helm, and release-boundary checks, use
the [contributing guide](../CONTRIBUTING.md#verification); for DLT recovery, use
the [Kafka retry/DLT runbook](runbooks/kafka-dlq.md).

## Related references

- [Code standards](code-standards.md)
- [Deployment guide](deployment-guide.md)
- [Documentation index](README.md)
