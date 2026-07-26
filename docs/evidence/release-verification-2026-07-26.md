# Release Verification Evidence — 2026-07-26

## Scope

- Branch: `feature/agricore-web-assistant-ck`
- Reviewed revision: `49a2614`
- Environment: Windows host, Docker Desktop 29.5.3, local Compose network
- Evidence bundle: `compose-ps.txt`, `mvn-test.log`, `e2e-flow.log`,
  `core-slice.http.log`, and `traceability.json`

This is local release evidence. It does not claim a production deployment,
production TLS/mTLS, Kafka ACL enforcement, or production observability
retention.

## Quality gates

| Gate | Result | Evidence |
|---|---|---|
| Full Maven reactor | PASS | 684 tests, 0 failures, 0 errors, 0 skipped; `BUILD SUCCESS` |
| PostgreSQL idempotency gate | PASS | `InventoryPostgresIdempotencyTest`: 3/3 cases executed |
| Assistant focused suite | PASS | 171 tests, 0 failures/errors/skips |
| Work focused suite | PASS | 69 tests, 0 failures/errors/skips |
| Frontend contracts | PASS | Generated Identity, Gateway, and Assistant clients have no drift |
| Frontend static gates | PASS | TypeScript, ESLint, production build, CSP, and same-origin script policy |
| Frontend tests | PASS | 85 files, 299 tests |
| Playwright journeys | PASS | 3/3: refresh rotation/headers, SSE reconnect, durable cancellation |
| Full Compose release gate | PASS | All 13 Spring applications, console, and required infrastructure healthy |
| Docker builds | PASS | All 14 application images built; affected Assistant, Work, and console images rebuilt after fixes |
| OTLP trace delivery | PASS | Gateway container exported to `tempo:4318`; Tempo returned 2 gateway traces |
| Frontend production image | PASS | Console image rebuilt, recreated, and health-checked |

The full Maven run predates only release-script, frontend, and Compose endpoint
changes in the final reviewed revision. No backend source changed after that
successful reactor run.

## Runtime acceptance path

The release gate exercised real JWTs, PostgreSQL, Kafka, Redis, and service
boundaries:

1. Invalid bearer tokens returned 401 at both gateway and direct farm-service.
2. Registration/login issued an RS256 access token.
3. Farm, plot, crop cycle, and legal stage transitions succeeded through the
   gateway; the illegal `PLANNED → COMPLETED` jump returned 409.
4. Work followed `CREATED → ASSIGNED → IN_PROGRESS → COMPLETED`.
5. Harvest completion wrote an outbox event and the Inventory consumer increased
   aggregate `COFFEE-ROBUSTA` stock by exactly 90 kg.
6. Traceability projected the deterministic full-UUID code and returned the
   public product, farm, and plot facts.
7. Republished `HarvestCompleted.v1` did not change stock; Inventory and
   Traceability processed-event ledgers each remained at one.
8. Two copies of a Sales event created one notification.
9. A wrong-version Harvest event was routed to the shared DLT by both projection
   consumers.

The final successful run reported:

```text
Inventory stocked sku=COFFEE-ROBUSTA before=6990.000 onHand=7080.000
Duplicate projection OK onHand=7080.000 inventoryLedger=1 traceabilityLedger=1
Notification dedupe OK count=1
Harvest DLT OK dltRecords=2
VERIFY PLATFORM OK
```

## Security and supply-chain checks

- Tracked-file scans found zero OpenAI-style keys, AWS access-key IDs, or private
  key PEM headers.
- Production frontend dependency audit reported no known vulnerabilities.
- Local JWT keys and `.env` remained ignored and untracked.
- Gitleaks, CodeQL, and Trivy remain mandatory CI gates. This report does not
  claim a local Trivy or CodeQL run.

## Seed and media checks

- Large seed dry-run: 32 farms, 24 plots per farm, 768 plots, no API or database
  writes.
- Repository media verifier: 4 assets, 1,274,833 bytes.
- Assets include three optimized WebP images and one 660,723-byte GIF; SHA-256
  values match `assets/media/agricore-showcase/manifest.json`.

## Disk and cache discipline

| Checkpoint | C: free | D: free |
|---|---:|---:|
| Before final image/test gates | 8.98 GB | 20.00 GB |
| After release verification | 8.04 GB | 19.96 GB |

Maven evidence and temporary files used `D:\caches`; Playwright browsers used
`D:\caches\ms-playwright`. Docker reported 21.49 GB of reclaimable images and
3.66 GB of reclaimable build cache, but no global prune was performed because
other user projects share the Docker engine.

## Remaining release boundary

- Push the feature branch and let GitHub CI rerun Maven `verify`, frontend,
  Gitleaks, Compose/Helm, CodeQL, and Trivy gates.
- Merge without squashing the focused commit history.
- Verify Docker Hub and GHCR package publication by digest.
- Production operators must still provide rotated secrets, TLS/mTLS policy,
  Kafka authentication/ACLs, external persistence, and retention policy.
