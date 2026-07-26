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
| Docker Hub publish gated on `ci` + `trivy` + `codeql` for the same SHA, SHA-pinned | Live |
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

Adding those advices exposed a defect in the shape they were copied from. An
`@ExceptionHandler(Exception.class)` catch-all takes precedence over Spring's
`DefaultHandlerExceptionResolver`, so it silently captured the framework's own web exceptions and
reported each as 500: a mistyped URL 500 instead of 404, a wrong HTTP method 500 instead of 405, a
malformed UUID path variable 500 instead of 400. `farm` and `identity` had carried this since their
advices were written; copying the pattern spread it to three more services before a probe caught it.

Fixed in all five. The catch-all now forwards `ErrorResponse.getStatusCode()` before falling through
to `INTERNAL_ERROR`, and `MethodArgumentTypeMismatchException` — which does not implement
`ErrorResponse` — has its own 400 handler. Six regression tests cover the four cases plus the
message not repeating the rejected value.

The six services without a catch-all were never affected: Spring's own resolver still handles those
exceptions there, with the correct status and Boot's default body. Giving them the `ApiError` shape
for 404 and 405 too would mean adding catch-alls, which is what caused this defect in the first
place, so it stays part of the remaining sweep rather than a quick follow-on.

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

### The public QR page always shows an empty variety and planting date

Found on 2026-07-27 while writing the listener test against the producer's real envelope, and
pinned by a test rather than left as a note.

`traceability_batches` stores `variety_name` and `planting_date`, `PublicTraceabilityResponse`
returns them, and the QR page a consumer scans renders them. `HarvestApplicationService` never emits
either field. So for every batch created through Kafka — which is the normal path — both are null,
and the page shows blanks where it promises provenance.

Only the REST backfill (`POST /api/v1/traceability/batches`) can populate them today, and nothing
calls it in production.

Closing it means the harvest producer carrying both fields, which means harvest knowing the crop
cycle's variety and planting date — it holds `cropCycleId`, so the data is reachable, but the
service does not currently join to it. That is a product decision about what the QR page promises,
not a wiring gap, so it is recorded rather than guessed at.

### Kafka paths have no end-to-end test

Every service's "integration" test is MockMvc over H2 with Kafka autoconfiguration excluded. There
is no produce-to-consume test anywhere, and the five `OutboxPublisher` copies have no tests at all.
Closing this means an embedded or containerised broker, which the disk budget currently rules out.

Partly mitigated as of 2026-07-27. Both listeners are now unit-tested against the exact envelope
their producer emits — notification's four cases, and traceability's fifteen. That covers envelope
parsing, event-type filtering, every fallback, and the throw that routes a poisoned message to the
DLT. What it still cannot cover is the wiring between them: that the producer's topic matches the
consumer's subscription, that serialization round-trips, and that the DLT is actually configured.
A fixture copied from the producer catches a field rename; only a broker catches a topic rename.

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

The libraries and farm are done. Measured 2026-07-27, whole reactor:

| Module | Instructions | Branches | | Module | Instructions | Branches |
|---|---|---|---|---|---|---|
| common-lib | 99.2% | 90.0% | | crop-catalog | 70.0% | 43.8% |
| common-security | 61.1% | 86.7% | | work | 68.2% | 39.3% |
| **farm** | **84.4%** | **77.8%** | | harvest | 67.2% | 38.5% |
| **traceability** | **80.9%** | **63.0%** | | inventory | 66.8% | 28.0% |
| api-gateway | 77.5% | 50.0% | | notification | 65.1% | 33.3% |
| crop-cycle | 74.8% | 52.3% | | sales | 64.8% | 40.7% |
| iot | 73.4% | 46.2% | | identity | 71.4% | 53.3% |

`common-lib` went from 20.6%/0%, `common-security` from 25.6%/30%, farm from **57.7%/4.2%**, and
traceability from **52.9%/16.7%**. `FarmApplicationService` went from 2 of 38 covered branches to
37; `HarvestCompletedKafkaListener` from 0 of 24 to 22.

Two classes are deliberately left uncovered, both because a test would assert the framework rather
than this platform's behaviour:

- `DomainServiceSecurityConfig` (common-security) — an autoconfiguration needing a Spring context,
  which every service test already boots.
- `ProcessedEventEntity.Pk` (traceability, 0 of 8 branches) — `@IdClass` `equals`/`hashCode` with no
  constructor or setters, so only Hibernate or reflection can populate it. Those 8 branches are the
  entire gap between traceability's 63% and the 65% target. Writing a reflection test to cross the
  line would be exactly the coverage theatre this work is meant to avoid; the behaviour that
  actually matters — dedup on `(event_id, consumer_name)` — is covered end to end.

**Branches remain the binding constraint** for the seven services still untouched.

Farm and traceability are the template: drive the application service directly for rejection and
partial-update paths, unit-test the Kafka listener against the producer's real envelope, and use
MockMvc for the advice. Farm took four test classes for +26 instruction points and +74 branch
points; traceability two for +28 and +46.

Sequence unchanged: lift service branch coverage, then flip the gate strict in its own change.
Binding `jacoco:check` today still fails every service module except farm.

### Branch protection on `main`

The publish half of this is done: `docker-publish` now requires `ci`, `trivy`, and `codeql` to have
concluded `success` for the exact SHA before it builds, so a commit that fails the vulnerability scan
or the SAST pass no longer ships twelve public images.

Branch protection itself remains open and remains the owner's call. `main` is unprotected, so nothing
stops a direct push or a force-push; the gate above only decides whether that push gets published.
Enabling required status checks and blocking force-push is a repository setting, and it changes the
working style for a solo contributor (feature branches and PRs instead of direct pushes), so it is
not something to switch on mid-flight.

### `NotificationRequested.v1` producer

The constant exists, no service emits it. notification-service consumes `UserRegistered.v1` instead.
Adding a producer means deciding which service owns "a notification should be sent" — a design
decision, not a wiring gap. Documented as unproduced everywhere it appears.

### IoT threshold alerting

`SensorThresholdExceeded.v1` and `DeviceOfflineDetected.v1` are naming reservations only. Alerting
needs threshold configuration per crop/device class plus a delivery channel, and the notification
service has no real delivery adapter yet.

### Endpoints the contract advertised but nobody built

Removed from `contracts/openapi/` on 2026-07-26 so the contract stops describing an API that does not
exist. Kept here because deleting the declaration should not also delete the intent:

| Endpoint | Note |
|----------|------|
| `POST /api/v1/crops` | Crops are seeded by Flyway migration; no write API exists. A generated client would have offered this call and received 405. |
| `GET /api/v1/crops/{cropId}/varieties` | The `crop_varieties` table and `uk_variety_crop_code` index exist; nothing reads them. The schema is ready, the endpoint was never written. |
| `GET /api/v1/iot/devices` | `IotController` registers devices and ingests readings; there is no way to list what is registered. |
| `GET /api/v1/notifications` | Notifications are written and read straight from the database. |

All four are small and none is blocked. They were removed rather than implemented because inventing
functionality to satisfy a stale document is the wrong direction — the code is the reference, and
these are now features to decide on rather than promises the contract was quietly breaking.

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

Everything actionable without an owner decision was taken on 2026-07-26 — the missing advices,
duplicate-key conflicts, library coverage, and the publish gate. What remains at the top of this list
is genuinely blocked on a decision, not on effort.

1. Decide the Helm deployment model (external datastores vs. subchart dependencies), then fix the
   three chart defects together. Highest priority: the chart currently cannot work.
2. Decide `DomainEventEnvelope`: adopt it in the five producers and generate the AsyncAPI schemas
   from it, or delete it. Leaving dead code that looks like the event contract is the worst option.
   Ten of the twelve produced event types are still undeclared in AsyncAPI, and this decision
   determines whether declaring them is generated or hand-written.
3. Enable branch protection on `main` (owner decision). Publication is now gated on all three
   workflows, but nothing yet stops a direct push or a force-push to the branch itself.
4. Lift service **branch** coverage, then flip the coverage gate strict. Instructions are close to
   target almost everywhere; branches are not, and `farm` at 4.2% is where to start.
5. Finish the API robustness sweep: `@Size` on `@NotBlank` fields backed by length-limited columns,
   `@Digits` on `@DecimalMin` numeric columns, and the `...IgnoreCase` finders whose
   `upper(col) = upper(?)` no plain unique index can serve.
6. Spring Boot 4 compat audit + ADR, then the coordinated migration.
7. Notification delivery adapter, which unblocks IoT alerting.
