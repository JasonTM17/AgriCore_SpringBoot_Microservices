# AgriCore deployment guide

## Deployment models

| Model | Repository support | Intended use |
|---|---|---|
| Docker Compose | Application, infrastructure, simulator, observability profiles | Local development and acceptance evidence |
| Helm chart | 13 Spring workloads, console, services, optional Ingress, policies/scaling/probes | Application deployment into an operator-managed cluster |

The Helm chart does not install production databases, Kafka, MQTT, Redis, object
storage, SMTP, ingress controller, certificate manager, or observability
backends.

## Required production inputs

- PostgreSQL-compatible databases and application credentials per service.
- TimescaleDB capability in the IoT database.
- Redis with authentication and persistence policy.
- Kafka topics, authorization, retention, and DLT operations.
- MQTT TLS, device credential lifecycle, and per-device ACLs.
- Private MinIO/S3-compatible object storage and bucket policy.
- SMTP credentials and sender/domain configuration.
- RSA signing key Secret, provider key Secret when enabled, and database Secret.
- Ingress/TLS/DNS policy and trusted proxy configuration.
- OTLP/metrics/log endpoints, sampling, storage, access, and retention.

Never copy values from `.env.example` into production unchanged.

## Local deployment

```powershell
Copy-Item .env.example .env
.\scripts\generate-jwt-keys.ps1
docker compose up -d postgres redis kafka kafka-ui kafka-topics-init mqtt minio
docker compose -f docker-compose.observability.yml up -d
docker compose up -d --build
```

Use [the local operations runbook](runbooks/local-operations.md) for health,
simulator, seed, verification, log, and cleanup commands.

## Helm release

1. Provision external dependencies and databases.
2. Create namespace-scoped Secrets outside Git.
3. Copy `values.yaml` to an environment-owned file and set image full-SHA tags or
   digests, endpoints, resources, ingress/TLS, and observability.
4. Validate before applying:

   ```bash
   helm lint infrastructure/helm/agricore -f values-production.yaml
   helm template agricore infrastructure/helm/agricore \
     -f values-production.yaml > rendered.yaml
   ```

5. Review rendered Secrets, service accounts, security contexts, NetworkPolicies,
   Jobs, probes, PDBs, HPAs, and Ingress hosts.
6. Deploy with an atomic timeout appropriate to migration duration:

   ```bash
   helm upgrade --install agricore infrastructure/helm/agricore \
     --namespace agricore --create-namespace \
     -f values-production.yaml --atomic --timeout 15m
   ```

7. Verify rollout, migrations, health, metrics, gateway JWT, public traceability,
   Kafka lag/DLT, and object-storage access.

## Database change and rollback

- Back up every affected database before an irreversible migration.
- Rehearse restore with the exact engine/extension version.
- Prefer expand/backfill/cutover/contract changes. Old application code must
  tolerate the expanded schema during a rolling update.
- Flyway migrations are forward-only. Application rollback does not automatically
  undo schema or published events.
- For a failed release, stop writers if data interpretation changed, restore the
  compatible application image, and follow the migration-specific runbook.

## Image verification

Release workflows publish `latest`, short SHA, and full SHA tags to Docker Hub
and GitHub Packages after eligible default-branch CI. Production should deploy a
full SHA tag or digest.

```bash
docker buildx imagetools inspect IMAGE@sha256:DIGEST
cosign verify IMAGE@sha256:DIGEST \
  --certificate-identity-regexp 'github.com/.+/.github/workflows/docker-publish.yml@refs/heads/(main|master)' \
  --certificate-oidc-issuer https://token.actions.githubusercontent.com
```

Store the merged revision, image digests, rendered values checksum, migration
versions, and verification evidence with the release record.
