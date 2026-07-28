# Changelog

All notable changes to this project are documented here.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project
intends to follow [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

The [prepared v1.0.1 release manifest](docs/releases/v1.0.1.md) defines the
next source-tag and workflow-evidence requirements. It is not a tag, GitHub
Release, CI result, or package-publication record. Packages remain immutable
short- and full-SHA tags, operators deploy the resolved digest, and `latest` is
never promoted.

## [Unreleased]

### v1.0.1 preparation

These entries describe the next maintenance source-release candidate. They do
not establish a published source release or successful release evidence.

#### Fixed

- Notification now source- and payload-validates a version-1
  `SensorReadingReceived.v1` before committing it as a no-op. Valid accepted
  telemetry no longer enters the IoT DLT; wrong source, topic, version, or
  payload schema still fails directly to that DLT.

#### Added

- Focused regression coverage for Identity authentication/signing, Inventory
  stock and harvest boundaries, and Sales saga recovery and Inventory-client
  behavior.

#### Documentation

- Prepared v1.0.1 provenance, deployment, security, evidence, and current
  documentation pointers while preserving v1.0.0 as historical.

## [1.0.0](docs/releases/v1.0.0.md) - 2026-07-28

### Added

- **13 Spring-application platform**: api-gateway, identity, farm, crop-catalog, crop-cycle,
  work, inventory, harvest, notification, iot, sales, traceability, and assistant — database per
  service, no shared schema.
- **Authentication** on identity-service: RS256 JWT with a JWKS endpoint, refresh-token rotation
  with opaque hashed tokens, login rate limiting, and account lockout.
- **`common-security` module** giving every service JWT resource-server validation against
  identity's JWKS, without a network hop per request.
- **Transactional outbox** on identity, farm, crop-cycle, work, and harvest — domain events are
  written in the same transaction as the aggregate and shipped by a polling publisher.
- **Idempotent Kafka consumers** on inventory, traceability, and notification, each with a
  processed-event marker committed alongside its side effect, plus retry backoff and a dead-letter
  topic.
- **`UserRegistered.v1` end to end**: identity publishes it through the outbox, notification
  consumes it and records a welcome notification.
- **Crop cycle lifecycle** with plot-overlap rejection and enforced stage transitions.
- **Harvest → inventory → traceability flow**: completed harvest batches produce stock movements
  and a public QR lookup served from a local read model.
- **Sales order saga** orchestrating inventory reservation and confirmation, with reconciliation of
  stuck reservations.
- **IoT sensor ingestion** with alert cooldown.
- **Deployment surface**: Docker Compose stack (app, infrastructure, observability), Helm charts,
  Kubernetes network policy, Prometheus and Tempo configuration.
- **CI/CD configuration**: Maven build and test, Gitleaks secret scan, Compose config validation,
  CodeQL SAST, Trivy vulnerability scan, and SHA-only candidate/promotion workflow gated on CI.
- **JaCoCo coverage measurement** per module with an advisory CI summary.
- **Documentation set**: per-service READMEs for all 13 Spring applications, system architecture,
  codebase summary, deployment guide, code standards, design guidelines, roadmap, PDR, and ADRs.
- **Repository-owned generated showcase media**: manifest and verifier cover 13 assets totaling
  1,608,664 bytes: 12 WebP files and one three-frame GIF.

### Changed

- Harvest `HarvestCompleted` payload enriched so traceability can project a QR view without calling
  back into harvest.
- Traceability QR lookup prefers `productName` carried on the harvest event over a local join.
- Docker image candidate builds capped at four concurrent matrix jobs after a burst of simultaneous
  runs exceeded the Docker Hub pull-rate limit.

### Fixed

- Sales saga releases the inventory reservation when the confirm step fails. Previously only
  network-level errors compensated; an HTTP error from the same call recorded the saga as
  `COMPENSATED` while the stock stayed held, and the order could not be recovered because reconcile
  rejects an order with no reservation id.
- An inventory optimistic-lock conflict is no longer reported as out of stock, so two concurrent
  orders on one item no longer cancel the loser while stock is available.
- Traceability resolves a redelivered event through an indexed lookup instead of loading every batch
  into memory.
- Registration gating and fail-closed login rate limiting on identity-service.
- Farm-service authorization denials now map to `403` instead of a generic error.
- Traceability batch-write endpoint is role-gated.
- Outbox publishing wired up for farm, crop-cycle, and work, which had writers but no publisher.
- Gateway upstream URLs and Redis connection injected correctly in the Helm chart.
- PostgreSQL `max_connections` raised and per-service Hikari pools capped, so the full compose stack
  can start without exhausting connections.
- Tempo configuration mounted, and Prometheus now scrapes the application services.
- Documentation corrected where it overstated reality: the ArchUnit test guards only that
  `common-lib` stays framework-free, and the gateway routes `/api/v1/admin/**` rather than the
  narrower `/api/v1/admin/users/**`.

### Security

- **Account lockout now takes effect.** The failed-login counter was written inside the transaction
  that rejects the attempt, and rejection throws an unchecked exception, so every increment was
  rolled back and no account ever locked. Failure state is now committed independently.
- **Refresh-token theft detection now takes effect.** Reuse of a revoked token revoked its family in
  the same rolled-back transaction, leaving a known-stolen family valid. Same fix.
- **Rate-limit and assistant-budget client identity is gateway-authenticated.** The gateway
  removes untrusted forwarding input, accepts `X-Forwarded-For` only from a configured immediate
  proxy, and HMAC-signs the canonical value for Identity and Assistant.
- **`POST /api/v1/notifications` requires `SYSTEM_ADMIN`.** It was the only mutating endpoint without
  a role check, and the caller chooses recipient, subject, and body.
- **The Spring Cloud Gateway actuator endpoint is no longer exposed.** It was reachable with any
  valid token and lists every internal upstream URI.
- Access tokens with a wrong or missing `aud` claim are rejected.
- Spring Boot pinned to 3.5.16 and PostgreSQL driver upgraded, for Actuator and driver CVEs.
- spring-kafka pinned to 3.3.16 for a deserialization CVE.

Both pins are deliberate. Dependabot proposals to move to Spring Boot 4 / Spring Cloud 2025.1 /
spring-kafka 4 were closed as one coordinated migration requiring a compatibility audit and an ADR,
not three independent bumps.
