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
- Ingress/TLS/DNS policy; a `GATEWAY_TRUSTED_PROXY_ADDRESS_PATTERN` matching
  only the immediate trusted ingress/load-balancer peer; and a shared client-IP
  signing Secret.
- OTLP/metrics/log endpoints, sampling, storage, access, and retention.

Never copy values from `.env.example` into production unchanged.
Service-local READMEs provide module setup and troubleshooting orientation;
deployment values, rendered manifests, and versioned contracts remain the
authority for an actual release.

### Authenticated client-IP propagation

The gateway strips or overwrites untrusted forwarding headers. It reads
`X-Forwarded-For` only when its immediate remote peer matches
`GATEWAY_TRUSTED_PROXY_ADDRESS_PATTERN`, canonicalizes the chosen IP, and signs
it with `AGRICORE_CLIENT_IP_SIGNING_SECRET`. Identity and Assistant accept only
the valid signed client-IP header pair; a missing, malformed, or invalid pair
falls back to the direct remote peer. This is not a general original-client-IP
provenance guarantee.

Compose requires the signing secret. Its dedicated `client-ip-edge` network
attaches only Console and Gateway: by default Gateway is `172.30.0.2`, Console
is `172.30.0.3`, and `GATEWAY_TRUSTED_PROXY_ADDRESS_PATTERN=172[.]30[.]0[.]3`
full-matches only Console. If the subnet changes, update `CLIENT_IP_EDGE_SUBNET`, `CLIENT_IP_EDGE_GATEWAY_IP`,
`CLIENT_IP_EDGE_CONSOLE_IP`, and `GATEWAY_TRUSTED_PROXY_ADDRESS_PATTERN`
together. For Helm, create the external Secret named by
`clientIp.signingSecretName` with key `clientIp.signingSecretKey`; it is mounted
only by Gateway, Identity, and Assistant. Never enable direct public ingress to
Identity or Assistant.

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
3. Copy `values.yaml` to an environment-owned file and set every service image
   to `repository@sha256:<digest>`, then configure endpoints, resources,
   ingress/TLS, client-IP Secret/pattern, and observability.
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

6. Create each enabled non-gateway service's `databaseSecretName` and the
   Gateway/Identity/Assistant client-IP signing Secret outside Git. The separate
   `postgres.provisioning.credentialSecretName` is mounted only by bounded hook
   Jobs, never application Deployments. Review rendered Secrets, service
   accounts, security contexts, NetworkPolicies, Jobs, probes, PDBs, HPAs, and
   Ingress hosts.
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
- Harvest and Inventory farm-scope migrations are additive. Harvest can
  re-authorize a legacy row from its stored plot; mismatched stored scope is
  masked as not found. Inventory fails closed when warehouse or processed-event
  scope is unavailable.
- Sales migration V8 is an executable release gate: it refuses to start while
  any customer/order lacks `farm_id` or an order disagrees with its customer's
  farm. Audit and explicitly backfill those rows from authoritative records
  before deploying V8. On success it makes both columns non-null and installs a
  composite foreign key so the mismatch cannot recur.
- Crop-cycle PostgreSQL migration V5 installs `btree_gist` and an exclusion
  constraint over inclusive planned date ranges for `DRAFT` and `ACTIVE` rows.
  Resolve any pre-existing overlapping active rows before migration; otherwise
  PostgreSQL will reject the constraint installation.
- Durable outbox retry migrations add nullable `next_attempt_at` and
  `quarantined_at`; legacy null rows remain immediately eligible. Their partial
  retry/quarantine indexes use `CREATE INDEX CONCURRENTLY` with
  `executeInTransaction=false`, so follow the service migration order and do
  not wrap them in an operator transaction.
- Testcontainers PostgreSQL coverage verifies the column types, valid partial
  indexes, due/deferred/quarantined filtering, and `FOR UPDATE SKIP LOCKED`
  non-blocking claims for Farm, Harvest, Identity, Notification, Sales,
  Traceability, and Work. Docker is required to execute those tests.

## Image verification

The repository configures a release workflow that, after eligible
default-branch CI, builds each image once and pushes a candidate to Docker Hub
and GitHub Packages. It scans the exact candidate digest, verifies registry
parity, signs it in both registries, promotes only short-SHA and full-SHA tags,
and re-verifies each promoted reference. All 14 Dockerfiles pin their build and
runtime bases by digest and accept `GIT_SHA` for the OCI revision label. It does
not publish `latest`. This is a configured supply-chain path, not proof that an
image has been published; production should deploy a verified full SHA tag or
digest.

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
