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

Partly done. `crop-catalog`, `notification`, and `traceability` had no `@RestControllerAdvice`, so
`ApiError` — documented as the uniform error body for all services — was not what those three
returned. All eleven servlet services now emit it. The gateway is excluded and stays excluded: it is
WebFlux, so servlet advice does not apply to it.

Duplicate keys are also done. All eleven advices now map a unique violation to 409
`DUPLICATE_RESOURCE`; a foreign-key or not-null violation stays 500, because it is a service defect
rather than a caller conflict. Classification is by SQLState, in `common-lib`'s
`ConstraintViolations` — a probe run before any handler existed showed Hibernate's JPA dialect
collapses every class 23 failure into one `DataIntegrityViolationException` and never narrows it to
`DuplicateKeyException`, so narrowing by subtype was not available. `23505` is verified against a
real PostgreSQL server, not only against H2 in PostgreSQL mode.

Still open, and still better done in one pass than piecemeal: `@NotBlank` without `@Size` on fields
backed by length-limited columns; `@DecimalMin` without `@Digits` on numeric columns; and
`...IgnoreCase` finders that emit `upper(col) = upper(?)`, which the plain unique indexes cannot
serve — the values are already uppercased before insert, so the `IgnoreCase` is not load-bearing.

Note on what an advice cannot reach: a 401 is produced by the security filter chain's
authentication entry point, before the DispatcherServlet, so it is not shaped by
`@RestControllerAdvice` in any service. That is uniform across the platform rather than a gap in
these three.

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

### Event contracts are declared but not specified

A completeness scan on 2026-07-26 quantified the gap. Twelve event types are produced; three of the
five AsyncAPI channels (farm, crop-cycle, work) carry no `messages:` block at all, so ten of the
twelve are undeclared. The two that are declared resolve to an envelope schema whose business
`payload` is a bare `{type: object}`, so no per-event shape exists for a consumer to validate
against. `EventTypes` declares 32 constants and 20 of them are never produced.

Related and worth deciding together: `DomainEventEnvelope` in `common-lib` is dead code — its only
reference is its own test, because all five producers hand-roll an `ObjectNode`. Either the
producers adopt it and the AsyncAPI schemas are generated from one definition, or it should be
deleted rather than left looking like the contract.

### Kafka paths have no end-to-end test

Every service's "integration" test is MockMvc over H2 with Kafka autoconfiguration excluded. There
is no produce-to-consume test anywhere, and the five `OutboxPublisher` copies have no tests at all.
The idempotency tests call the application service directly, so the listener, the error handler, and
the DLT routing are only exercised by unit tests with a mocked service. Closing this means an
embedded or containerised broker, which the disk budget currently rules out.

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

Coverage is measured but not enforced. Targets are ≥ 70% instructions / 65% branches for services and
≥ 90%/85% for critical modules (identity, inventory, sales).

The libraries were the first step and are done. Measured on 2026-07-26, whole reactor:

| Module | Instructions | Branches | | Module | Instructions | Branches |
|---|---|---|---|---|---|---|
| common-lib | 99.2% | 90.0% | | inventory | 66.8% | 28.0% |
| common-security | 61.1% | 86.7% | | harvest | 67.2% | 38.5% |
| api-gateway | 77.5% | 50.0% | | work | 68.2% | 39.3% |
| crop-cycle | 74.8% | 52.3% | | crop-catalog | 69.9% | 40.0% |
| identity | 73.6% | 57.1% | | sales | 64.8% | 40.7% |
| iot | 73.4% | 46.2% | | farm | 57.7% | 4.2% |
| notification | 71.0% | 38.9% | | traceability | 57.1% | 18.8% |

`common-lib` went from 20.6%/0% and `common-security` from 25.6%/30%. `DomainServiceSecurityConfig`
is the only class still at zero: it is an autoconfiguration and needs a Spring context, which every
service test already boots — a unit test for it would assert the framework, not the config.

**Branches are the binding constraint, not instructions.** No service meets the 65% branch target,
and farm at 4.2% is an outlier worth its own look: its advice has an `Exception.class` catch-all and
its integration test covers two happy paths, so almost every decision in the module is unexecuted.

Three services read lower than they did before 2026-07-26 — notification 77.3% → 71.0%, and
crop-catalog and traceability similarly. That is this pass adding error-path code, not coverage being
lost: each gained an advice whose 500 and validation branches no service currently exercises end to
end. Real behaviour improved and the percentage fell, which is the honest direction for that trade.

Sequence unchanged: lift service branch coverage, then flip the gate strict in its own change.
Binding `jacoco:check` today fails every service module.

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
   whole class of 500s into correct 409s across eight services. No service handles it today. Note
   that a shared advice module is not the obvious win it looks like: `farm` already has an
   `Exception.class` catch-all, so cross-advice ordering would be undefined, and the gateway is
   WebFlux while `common-security` carries servlet web.
4. Decide `DomainEventEnvelope`: adopt it in the five producers and generate the AsyncAPI schemas
   from it, or delete it. Leaving dead code that looks like the event contract is the worst option.
5. Lift `common-lib` / `common-security` coverage, then flip the coverage gate strict.
6. Spring Boot 4 compat audit + ADR, then the coordinated migration.
7. Notification delivery adapter, which unblocks IoT alerting.
