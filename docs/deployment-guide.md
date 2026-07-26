# Deployment Guide

Three paths: local compose (development), published images (demo/staging), Helm on Kubernetes
(cluster). Local infrastructure ports are deliberately non-standard to avoid clashing with other
stacks on the same machine.

## 1. Local: infrastructure only

```powershell
.\scripts\dev-up.ps1        # Windows
```

```bash
./scripts/dev-up.sh         # Linux/macOS
```

Starts PostgreSQL (host **5434**, multi-database), Redis (**6380**), Kafka (**9092**), and Kafka UI
(<http://localhost:8088>). Services then run from your IDE or `mvn spring-boot:run`.

Databases are created by `infrastructure/docker/postgres/init-databases.sql`; each service applies its
own Flyway migrations at startup.

## 2. Local: full stack in containers

```bash
docker compose up --build
```

Boots infrastructure plus all twelve services with healthcheck-gated startup ordering. The gateway
listens on <http://localhost:8080>.

Before first boot, generate stable JWT signing keys — without them identity generates ephemeral keys
and every restart invalidates issued tokens:

```powershell
.\scripts\generate-jwt-keys.ps1
```

Keys land in `infrastructure/jwt/` (gitignored) and are mounted read-only into the identity container.

Observability is a separate opt-in file:

```bash
docker compose -f docker-compose.yml -f docker-compose.observability.yml up
```

## 3. Verify a deployment

```powershell
.\scripts\verify-platform.ps1 -EvidenceDir C:\path\to\evidence
```

Builds and starts the full stack, runs the Maven suite, then executes the gateway JWT end-to-end path
(farm → cycle → work → harvest → Kafka → inventory → QR) and writes an evidence bundle:
`compose-ps.txt`, `mvn-test.log`, `e2e-flow.log`, `traceability.json`, `git-log.txt`.

With the stack already up, `scripts/e2e-happy-path.ps1` runs the flow alone.

## 4. Published images

Images live under the `nguyenson1710` Docker Hub namespace, one per service
(`nguyenson1710/agricore-<service>`), each tagged `latest`, short SHA, and full commit SHA.

Publication is **causally gated**: `docker-publish.yml` triggers only on a completed `ci`
`workflow_run` that was a `push` to `main`/`master`, concluded `success`, and came from this
repository. It then checks out `workflow_run.head_sha` explicitly and builds that immutable revision —
never a branch tip that may have moved. A failed, cancelled, or PR-triggered `ci` run publishes
nothing.

`ci` is necessary but no longer sufficient. Before building, `resolve-sha` reads back the `trivy`
and `codeql` conclusions **for that same SHA** and requires both to be `completed` + `success`.
Previously those two ran alongside `ci` and gated nothing, so a commit failing the CRITICAL/HIGH
vulnerability scan or the SAST pass still shipped twelve public images.

Two consequences worth knowing before wondering where an image went:

- **A scan still running when `ci` finishes skips the publish** rather than waiting for it. Polling
  would burn runner minutes on every push, and a skip is recoverable. The run log always names which
  workflow blocked it and why.
- **A missing scan run blocks.** Absence of evidence is not a pass, so a renamed workflow fails
  closed instead of waving builds through.

Recovery for either: `workflow_dispatch` on `main`. That path is deliberately exempt from the scan
gate — it is the lever for a publish the gate skipped — and it logs that it bypassed.

`ci` remains the only trigger. Listing all three workflows under `workflow_run` would fire this
workflow three times per push, each firing aware only of its own trigger's conclusion, and the
concurrency group serialises rather than cancels — so the same twelve images would be built and
pushed three times.

Required repository secrets (names only): `DOCKERHUB_USERNAME`, `DOCKERHUB_TOKEN`. Manage them at
`Settings → Secrets and variables → Actions`. The scan check uses the built-in `GITHUB_TOKEN` with
`actions: read`; no additional secret is needed.

Manual `workflow_dispatch` is allowed but still refuses any ref other than `main`/`master`.

## 5. Kubernetes (Helm)

```bash
helm upgrade --install agricore infrastructure/helm/agricore \
  --set global.imageTag=<git-sha> \
  --namespace agricore --create-namespace
```

The chart renders one Deployment per enabled service from `values.yaml`. Every pod receives the shared
database, Kafka, and JWT issuer environment; identity additionally receives Redis and registration
settings, sales receives the inventory URL, and the gateway receives every upstream service URL.

Security posture baked into the template: `runAsNonRoot`, `runAsUser: 10001`,
`seccompProfile: RuntimeDefault`, per-service CPU/memory requests and limits.

Database credentials come from the `agricore-db` secret (`username`, `password`) — see
`templates/secret-template.yaml`. Create it out of band; never commit real values.

Pin `global.imageTag` to a SHA tag rather than `latest` so a rollback is a value change, not a rebuild.
`infrastructure/k8s/network-policy.yaml` restricts pod-to-pod traffic and is applied separately.

## 6. Configuration reference

Shared across services: `POSTGRES_HOST`, `POSTGRES_PORT`, `POSTGRES_USER`, `POSTGRES_PASSWORD`,
`KAFKA_BOOTSTRAP_SERVERS`, `JWT_ISSUER`, `IDENTITY_JWKS_URI`, `AGRICORE_DEV_MODE`.

Service-specific variables and their defaults are documented in each service's README. Production
must set `AGRICORE_DEV_MODE=false`, `AGRICORE_REGISTRATION_ENABLED=false`,
`AGRICORE_RATE_LIMIT_FAIL_OPEN=false`, and real JWT key paths.

## 7. Rollback

- **Compose** — `docker compose down`, then bring up the previous image tags.
- **Helm** — `helm rollback agricore <revision>`; because images are SHA-tagged, the previous revision
  pulls the exact prior build.
- **Database** — Flyway migrations are forward-only. A rollback that must undo a schema change needs a
  new compensating migration, not a reverted file.

## 8. Known gaps

- `main` is not branch-protected, so CI is the only gate before publish. Tracked in
  [project-roadmap.md](project-roadmap.md).
- No staging environment: verification happens locally via `verify-platform` and in CI.
- Notification delivery is a log sink; no SMTP or webhook adapter is deployed.
