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
- Assistant archived-conversation, audit, replay-event, and cleanup retention
  policy approved for the deployment's legal and recovery requirements.
- Ingress/TLS/DNS policy and trusted proxy configuration.
- OTLP/metrics/log endpoints, sampling, storage, access, and retention.

Never copy values from `.env.example` into production unchanged.
Service-local READMEs provide module setup and troubleshooting orientation;
deployment values, rendered manifests, and versioned contracts remain the
authority for an actual release.

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
3. Copy `values.yaml` to an environment-owned file and set
   `global.imageTag` to the full-SHA tag, then configure endpoints, resources,
   ingress/TLS, and observability. The current chart renders `image:tag`;
   deploy-by-digest requires an operator-owned manifest overlay.
   The chart makes every application root filesystem read-only and supplies a
   bounded writable `/tmp` `emptyDir`. Keep the `api-gateway` Service alias
   because the Console image uses that portable upstream name.
4. Decide egress policy. The default allows egress to operator-managed
   dependencies. To restrict it, set `networkPolicy.restrictEgress=true` and
   provide PostgreSQL, Redis, Kafka, SMTP, MQTT, object-storage, JWKS, and OTLP
   rules through `networkPolicy.additionalEgress`; DNS and same-namespace
   AgriCore traffic are included by the chart.
5. Validate before applying:

   ```bash
   helm lint infrastructure/helm/agricore -f values-production.yaml
   helm template agricore infrastructure/helm/agricore \
     -f values-production.yaml > rendered.yaml
   ```

6. Review rendered Secrets, service accounts, security contexts, NetworkPolicies,
   Jobs, probes, PDBs, HPAs, and Ingress hosts.
7. Deploy with an atomic timeout appropriate to migration duration:

   ```bash
   helm upgrade --install agricore infrastructure/helm/agricore \
     --namespace agricore --create-namespace \
     -f values-production.yaml --atomic --timeout 15m
   ```

8. Verify rollout, migrations, health, metrics, gateway JWT, public traceability,
   Kafka lag/DLT, and object-storage access.

## Notification delivery

- Identity registration publishes `UserRegistered.v1` through its transactional
  outbox. Notification validates the source and bounded payload before creating
  an idempotent welcome-email delivery.
- External channels use at-most-once automatic delivery. They receive one
  automatic provider attempt; an adapter failure is persisted, and a stale
  `DELIVERING` lease becomes `FAILED` with `DELIVERY_OUTCOME_UNKNOWN` instead
  of being resent.
- `IN_APP` delivery is a local idempotent write and may be retried within the
  configured bounded attempt budget.
- This policy prevents automatic duplicate email/SMS after an ambiguous
  provider response. It does not guarantee that a provider delivered a
  `DELIVERY_OUTCOME_UNKNOWN` message.

Operators must monitor `FAILED` notification outcomes and reconcile ambiguous
external deliveries manually against provider evidence before any resend.

## Database change and rollback

- Back up every affected database before an irreversible migration.
- Rehearse restore with the exact engine/extension version.
- Prefer expand/backfill/cutover/contract changes. Old application code must
  tolerate the expanded schema during a rolling update.
- Flyway migrations are forward-only. Application rollback does not automatically
  undo schema or published events.
- For a failed release, stop writers if data interpretation changed, restore the
  compatible application image, and follow the migration-specific runbook.
- Harvest and Sales farm-scope migrations are additive: new rows persist
  `farm_id`, while pre-scope rows remain nullable for upgrade compatibility.
  Harvest can re-authorize a legacy row from its stored plot; mismatched stored
  scope is masked as not found. Sales and Inventory fail closed when legacy
  order/customer, warehouse, or processed-event scope is unavailable. Backfill
  those rows from authoritative farm/plot/warehouse records before relying on
  them after upgrade.
- Crop-cycle PostgreSQL migration V5 installs `btree_gist` and an exclusion
  constraint over inclusive planned date ranges for `DRAFT` and `ACTIVE` rows.
  Resolve any pre-existing overlapping active rows before migration; otherwise
  PostgreSQL will reject the constraint installation.

## Image verification

After eligible default-branch CI, the release workflow builds each image once
and pushes a candidate to Docker Hub and GitHub Packages. It scans the exact
candidate digest, verifies registry parity, signs the digest in both registries,
promotes only short-SHA and full-SHA tags, and re-verifies every promoted
reference. It does not publish `latest`. Production should deploy a full SHA tag
or digest.

```bash
docker buildx imagetools inspect IMAGE@sha256:DIGEST
cosign verify IMAGE@sha256:DIGEST \
  --certificate-identity-regexp 'github.com/.+/.github/workflows/docker-publish.yml@refs/heads/(main|master)' \
  --certificate-oidc-issuer https://token.actions.githubusercontent.com
```

Store the merged revision, image digests, rendered values checksum, migration
versions, and verification evidence with the release record.

Docker Hub publishing requires `DOCKERHUB_USERNAME` and `DOCKERHUB_TOKEN` as
repository secrets. GitHub Packages uses the workflow token's package write
permission. If either registry, scan, parity check, promotion, or signing step
fails, do not treat the other registry or a candidate tag as a complete release.
