# Project Roadmap

Status as of 2026-07-26. Deferred items carry the reason they were deferred, so the decision is
reviewable rather than forgotten.

## Shipped

| Area | State |
|------|-------|
| 12 services, database per service, Flyway | Live on `main` |
| Gateway JWT RS256 + JWKS local verification, `aud` enforcement | Live |
| Identity: register/login/refresh rotation/logout, RBAC, lockout, Redis rate limit (fail-closed) | Live |
| Transactional outbox + polling publisher | identity, farm, crop-cycle, work, harvest |
| Idempotent Kafka consumers with DLT | inventory, traceability, notification |
| Sales saga: reserve → confirm, plus reconcile for stuck reservations | Live |
| Public QR traceability read model | Live |
| Compose stack, Helm chart, NetworkPolicy | Live |
| CI: build/test, Gitleaks (hard fail), compose config validation | Live |
| CodeQL SAST, Trivy filesystem scan | Live |
| Docker Hub publish gated on successful default-branch `ci`, SHA-pinned | Live |
| JaCoCo coverage measurement, all modules | Live (advisory) |
| Per-service READMEs, platform documentation set | Live |

## Audit, 2026-07-26

A four-reviewer adversarial pass over security/auth, messaging/saga, persistence/API, and
build/CI/infra. Reports live outside the repo; the outcomes that matter are recorded here.

Six defects shared one property worth naming: **the defence was present in the code and absent at
runtime.** CI was green throughout, because nothing tested the behaviour end to end.

Fixed in this pass:

| Defect | Why it was invisible |
|--------|----------------------|
| Account lockout never persisted | The counter was written, then the rejection threw an unchecked exception and rolled the write back. |
| Refresh-token family revocation never persisted | Same mechanism, same transaction. |
| Login rate limit keyed on a forgeable header | Read the first `X-Forwarded-For` hop, which the caller writes; rotating it minted a fresh bucket per request. |
| Saga confirm-failure never compensated | Recorded `COMPENSATED` without releasing, so the stock stayed held and the saga row said otherwise. |
| Optimistic-lock 409 read as out of stock | Concurrent orders on one item terminally cancelled the loser with stock available. |
| Traceability replay scanned the whole table | `findAll()` on a Kafka redelivery path while the index for it sat unused. |
| `POST /api/v1/notifications` open to any token | The only endpoint on the platform without a role check; caller picks recipient and body. |
| `/actuator/gateway` readable by any token | Exposed but not in the permitAll list, so it fell through to `authenticated()`. |

Each carries a regression test that fails against the previous behaviour. The lockout fix was
falsified explicitly: with the write folded back into the rejecting transaction, all three assertions
report a counter of zero.

## Deferred, with reasons

### Helm chart is not installable as shipped

Three defects found in the same pass, all in the Helm path while the compose path is healthy:
identity mounts no JWT key material, so `RsaKeyProvider` generates an ephemeral keypair and every pod
restart invalidates every issued token; `templates/secret-template.yaml` re-applies `CHANGE_ME` over
a manually created secret on each `helm upgrade`; and the chart declares no datastore dependencies,
so `helm install` yields twelve pods with no PostgreSQL to reach.

Deferred because the fix depends on a deployment decision nobody has made yet — whether the target
cluster is expected to bring its own PostgreSQL, Redis, and Kafka, or the chart should carry
subchart dependencies. Guessing would bake the wrong assumption into the chart. Until then, compose
is the supported path and the chart should be treated as illustrative.

### Identity is reachable without the gateway in the compose stack

`docker-compose.yml` publishes identity on host `8081` while setting
`AGRICORE_TRUST_FORWARDED_HEADERS: "true"`. Requests that arrive that way have no gateway-appended
hop, so a forged `X-Forwarded-For` is the only entry present and becomes the rate-limit key.

Scope of the residual risk, stated precisely: this evades the **per-IP rate limiter**, not the
**per-account lockout**. Lockout is keyed on the user row and now actually persists, so repeated
guesses against one account still lock it at the threshold regardless of the claimed address. The two
controls are meant to be paired for exactly this reason — neither is sufficient alone.

Closing it properly means either dropping the host port publish (which the local e2e and seed scripts
currently depend on) or trusting the forwarded header only from known proxy addresses. Both are
deployment-shape decisions rather than code fixes, so neither was guessed at here.

### API robustness sweep

Systemic, low-blast-radius, and better done in one pass than piecemeal: no
`DataIntegrityViolationException` handler in any service, so a duplicate-code race returns 500 where
the single-threaded path returns 409; `@NotBlank` without `@Size` on fields backed by
length-limited columns; `@DecimalMin` without `@Digits` on numeric columns; three services with no
`@RestControllerAdvice`, so their errors do not match the platform `ApiError` shape; and
`...IgnoreCase` finders that emit `upper(col) = upper(?)`, which the plain unique indexes cannot
serve — the values are already uppercased before insert, so the `IgnoreCase` is not load-bearing.

### Concurrency invariants that rely on check-then-act

Crop-cycle plot-overlap rejection and IoT open-alert dedup both read-then-write with no database
constraint behind them, so two concurrent requests can both pass the check. Each needs an exclusion
or partial-unique constraint, which means a migration per service.

### Outbox and consumer conventions

Platform-wide and deliberately consistent rather than individually correct: the polling publisher
blocks on `send().get()` inside its transaction, `findUnpublished` has no `publish_attempts` cutoff
so a poisoned row retries forever, and listeners match event types with `contains` rather than
equality. Worth one ADR-backed pass across all five publishers and three consumers, not five
divergent fixes.

### Spring Boot 4 migration

Dependabot proposed Spring Boot 4.1.0, Spring Cloud 2025.1.2, and spring-kafka 4.1.0. All three were
closed together on 2026-07-26: they are one coordinated migration, not three bumps. Boot 4 moves the
Spring Framework 7 baseline across twelve services, and spring-kafka 4.x would also revert the
deliberate 3.3.16 CVE pin. Needs a compat audit plus an ADR before acceptance.

### Testcontainers 2.x

Closed 2026-07-26. Testcontainers 2.x is a breaking API rewrite; `InventoryPostgresIdempotencyTest`
and the failsafe setup depend on 1.x. The fail-closed behavior must be re-verified after migration, so
it needs its own audit.

### Strict coverage thresholds

Coverage is measured but not enforced. Measured baseline: identity 72.1% instructions / 48.7% branches,
notification 77.3% / 46.7%, common-lib 20.6% / 0%, common-security 25.6% / 30%. Targets are ≥ 70%/65%
for services and ≥ 90%/85% for critical modules (identity, inventory, sales), so binding
`jacoco:check` today would fail the build everywhere. Sequence: raise the library and branch coverage
first, then flip the gate strict in its own change.

### Branch protection on `main`

`main` is currently unprotected; CI is the only thing standing between a push and an image publish.
Enabling required status checks and blocking force-push is a config change in repository settings, and
it will change the working style for a solo contributor (feature branches + PRs instead of direct
pushes). Deliberately left as the owner's call rather than switched on mid-flight.

### `NotificationRequested.v1` producer

The constant exists, no service emits it. notification-service consumes `UserRegistered.v1` instead.
Adding a producer means deciding which service owns "a notification should be sent" — a design
decision, not a wiring gap. Documented as unproduced everywhere it appears.

### IoT threshold alerting

`SensorThresholdExceeded.v1` and `DeviceOfflineDetected.v1` are naming reservations only. Alerting
needs threshold configuration per crop/device class plus a delivery channel, and the notification
service has no real delivery adapter yet.

### Notification delivery adapter

Notifications are recorded, never actually sent. A real channel (SMTP, webhook, or n8n workflow) goes
behind the application service; the REST API and event contract do not change.

### Assistant service

Researched (`plans/reports/research-2026-07-18-agricore-assistant-architecture.md`): a Spring AI
service with its own database, SSE streaming, read-only domain tools, and no RAG on day one. Work
lives on `feature/agricore-web-assistant*`; the empty `services/assistant-service/` skeleton was
removed from `main` so the tree does not advertise a service that does not exist.

### Frontend

Design-only so far (Stitch operations console + public traceability). No frontend service exists in
this repository. When it lands it must be a separate service with its own container, generating its
API client from the committed OpenAPI contracts.

## Next candidates, in order

1. Decide the Helm deployment model (external datastores vs. subchart dependencies), then fix the
   three chart defects together. Highest priority: the chart currently cannot work.
2. Enable branch protection on `main` (owner decision), and gate image publication on `trivy` and
   `codeql` rather than `ci` alone.
3. API robustness sweep — the `DataIntegrityViolationException` handler first, since it converts a
   whole class of 500s into correct 409s across eight services.
4. Lift `common-lib` / `common-security` coverage, then flip the coverage gate strict.
5. Spring Boot 4 compat audit + ADR, then the coordinated migration.
6. Notification delivery adapter, which unblocks IoT alerting.
