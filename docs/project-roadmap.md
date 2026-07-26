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

## Deferred, with reasons

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

1. Lift `common-lib` / `common-security` coverage, then flip the coverage gate strict.
2. Enable branch protection on `main` (owner decision).
3. Spring Boot 4 compat audit + ADR, then the coordinated migration.
4. Notification delivery adapter, which unblocks IoT alerting.
