# Release Verification Evidence - 2026-07-26

## Scope

- Evidence type: historical local release-candidate snapshot, not current-HEAD
  completion proof.
- Branch: `feature/agricore-web-assistant-ck`
- Reviewed source revision: `5867b37`
- Runtime image content revision: `2c8c339`; the later `5867b37` commit
  changes only the GitHub Actions frontend toolchain.
- Environment: Windows host, Docker Desktop 29.5.3, local Compose network.
- Runtime and security captures were disposable host-local artifacts. Their
  qualified findings are preserved in this document; the capture bundle was
  cleaned after the later release closeout.

This is local release-candidate evidence. It does not claim a production
deployment, production TLS/mTLS, Kafka ACL enforcement, external persistence,
or production retention policy.

Later commits through the 2026-07-26 pre-landing checkpoint add notification
inbox persistence, invalid-payload handling, per-device MQTT admission, Console
session/media hardening, PostgreSQL crop-cycle overlap exclusion, authoritative
Harvest/Inventory/Sales farm scope, Helm/Compose dependency wiring, SHA-only
release promotion, and merged dependency upgrades. The results below were not
re-run against that complete change set. They must not be cited as a current
clean-revision pass.

## Quality gates

| Gate | Result | Evidence |
|---|---|---|
| Full Maven reactor | PASS | Exit 0 after 431 seconds; 247 current reports, 738 tests, 0 failures, 0 errors, 1 skipped |
| Frontend contracts | PASS | Generated Identity, Gateway, and Assistant clients have no drift |
| Frontend static gates | PASS | pnpm 11.17.0; ESLint, TypeScript, production build, CSP, and same-origin script policy |
| Frontend unit tests | PASS | 85 files, 299 tests |
| Playwright journeys | PASS | 3/3: refresh rotation/headers, SSE reconnect, durable assistant cancellation |
| Compose release gate | PASS | 13 Spring applications, console, and required infrastructure healthy |
| Runtime acceptance | PASS | JWT, CRUD, Kafka projections, public QR, idempotency, dedupe, and DLT |
| Public read load profile | PASS | 1,273 requests, 0 failures, p95 38.79 ms, p99 57.37 ms |
| Observability | PASS | Prometheus 13/13 UP; Grafana database OK; Tempo/Loki ready; Alloy healthy |
| Runtime image scans | PASS | 14 images, 27 OS/JAR targets, 0 HIGH/CRITICAL; no unfixed-vulnerability exception |
| Console build-stage scan | PASS | Node/pnpm build stage, 2 targets, 0 HIGH/CRITICAL |
| Filesystem scan | PASS | 18 dependency manifests, 0 HIGH/CRITICAL |
| Secret history scan | PASS | Gitleaks scanned 427 reachable commits with no leaks |
| Deployment/config gates | PASS | actionlint, Compose config, runtime contracts, Helm lint and render |
| Showcase media | PASS | 4 tracked assets, 1,262,468 bytes, manifest and checksums verified |

The historical Maven run includes Spring Boot 3.5.16, Spring Cloud 2025.0.2,
Gateway 4.3.4, Netty 4.1.136.Final, and Bouncy Castle 1.84. Within the captured
`5867b37` evidence sequence, only frontend container, media packaging, and CI
files changed after that reactor run. Subsequent commits do change Maven
plugins, backend source, migrations, contracts, and deployment configuration,
so this qualification does not extend to the current pre-landing checkpoint.

## Runtime acceptance path

The final Compose run used the locally built and scanned application images.
It exercised real JWTs, PostgreSQL, Kafka, Redis, MinIO, and service boundaries:

1. Missing credentials returned 401 at the edge and direct Farm API.
2. Registration and login issued an RS256 access token.
3. Farm, plot, crop cycle, and legal stage transitions succeeded through the
   gateway; an illegal `PLANNED -> COMPLETED` transition returned 409.
4. Work followed `CREATED -> ASSIGNED -> IN_PROGRESS -> COMPLETED`.
5. Harvest completion wrote an outbox event and Inventory increased
   `COFFEE-ROBUSTA` stock by exactly 90 kg.
6. Traceability projected the deterministic full-UUID code and returned public
   product, farm, and plot facts.
7. Republished `HarvestCompleted.v1` did not change stock; Inventory and
   Traceability processed-event ledgers each remained at one.
8. Two copies of one Sales event created one notification.
9. A wrong-version Harvest event reached the shared DLT for both projection
   consumers.

The successful final run reported:

```text
Inventory stocked sku=COFFEE-ROBUSTA before=14206.000 onHand=14296.000
Duplicate projection OK onHand=14296.000 inventoryLedger=1 traceabilityLedger=1
Notification dedupe OK count=1
Harvest DLT OK dltRecords=2
VERIFY PLATFORM OK
```

The evidence bundle contains `compose-ps.txt`, `e2e-flow.log`,
`core-slice.http.log`, `traceability.json`, `k6-summary.json`, and
`git-log.txt`.

## Security and supply chain

- Trivy scanned all 14 final runtime tags and the final Console build stage.
  Every report contains zero HIGH or CRITICAL findings.
- The Console build stage uses Node 22.23.1 and Corepack-managed pnpm 11.17.0;
  npm is removed after tool activation to reduce the build-stage surface.
- The Console runtime uses Nginx 1.30.2 on Alpine 3.23 with patched `c-ares`,
  `curl`, `libcurl`, and `libexpat`.
- Gitleaks full-history mode reported no leaks. The worktree has no tracked or
  non-ignored uncommitted files.
- Local `.env`, JWT private keys, build outputs, and test reports remain ignored
  and untracked.
- CodeQL remains a required remote CI gate; this local report does not claim a
  local CodeQL run.

## Seed and media

The large connected seed ran twice to prove idempotency:

- 32 farms and 768 plots
- 32 crop cycles and 128 work tasks
- 32 harvests, inventory batches, and IoT devices
- 640 telemetry readings
- 16 sales orders and 16 notifications
- 1 assistant conversation
- 32 MinIO attachment prefixes

The showcase verifier confirmed three WebP images and one bounded GIF. The GIF
is 648,358 bytes, 960x540, three frames, 3.6 seconds, and has SHA-256:

```text
7d81f16920cdfb0467a67b802695609181c87213a337759191e1ac91b8e7f524
```

The same GIF hash was verified inside the production Console image, together
with all three WebP assets and `manifest.json`.

## Disk and cache discipline

| Checkpoint | C: free | D: free |
|---|---:|---:|
| Before final dependency/image gates | 8.70 GB | 25.97 GB |
| After final runtime and evidence gates | 8.42 GB | 27.18 GB |

Playwright browsers and Trivy databases used bounded caches on `D:`. Temporary
CI and builder image tags were removed by exact name. No global Docker prune
was run because other user projects share the engine. Docker still reports
reclaimable shared images, volumes, and build cache; those were intentionally
left untouched.

## Historical release boundary

- The actions listed here were the next steps for the historical candidate, not
  current work. The integrated revision and package verification are recorded
  in [release closeout 2026-07-28](release-closeout-2026-07-28.md).
- Production operators must provide rotated secrets, TLS/mTLS, Kafka
  authentication/ACLs, external persistence, backups, and retention policy.
