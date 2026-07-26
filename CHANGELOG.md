# Changelog

All notable changes to this project are documented here.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project
intends to follow [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

No version has been tagged yet. Everything below is unreleased and lives on `main`; published
images are tagged `latest` plus the short and full commit SHA.

## [Unreleased]

### Added

- **Twelve-service platform**: api-gateway, identity, farm, crop-catalog, crop-cycle, work,
  inventory, harvest, notification, iot, sales, traceability — database per service, no shared
  schema.
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
- **CI/CD**: Maven build and test, Gitleaks secret scan, compose config validation, CodeQL SAST,
  Trivy vulnerability scan, and image publication to Docker Hub gated on `ci`.
- **JaCoCo coverage measurement** per module with an advisory CI summary.
- **Documentation set**: per-service READMEs for all twelve services, system architecture, codebase
  summary, deployment guide, code standards, design guidelines, roadmap, PDR, and seven ADRs.

### Changed

- Harvest `HarvestCompleted` payload enriched so traceability can project a QR view without calling
  back into harvest.
- Traceability QR lookup prefers `productName` carried on the harvest event over a local join.
- Docker image publication capped at four concurrent matrix jobs, after a burst of simultaneous runs
  exceeded the Docker Hub pull-rate limit and failed all twelve builds.

### Fixed

- Registration gating and fail-closed login rate limiting on identity-service.
- Farm-service authorization denials now map to `403` instead of a generic error.
- Traceability batch-write endpoint is role-gated.
- Outbox publishing wired up for farm, crop-cycle, and work, which had writers but no publisher.
- Gateway upstream URLs and Redis connection injected correctly in the Helm chart.
- PostgreSQL `max_connections` raised and per-service Hikari pools capped, so the full compose stack
  can start without exhausting connections.
- Tempo configuration mounted, and Prometheus now scrapes all twelve services.
- Documentation corrected where it overstated reality: the ArchUnit test guards only that
  `common-lib` stays framework-free, and the gateway routes `/api/v1/admin/**` rather than the
  narrower `/api/v1/admin/users/**`.

### Security

- Access tokens with a wrong or missing `aud` claim are rejected.
- Spring Boot pinned to 3.5.12 and PostgreSQL driver upgraded, for Actuator and driver CVEs.
- spring-kafka pinned to 3.3.16 for a deserialization CVE.

Both pins are deliberate. Dependabot proposals to move to Spring Boot 4 / Spring Cloud 2025.1 /
spring-kafka 4 were closed as one coordinated migration requiring a compatibility audit and an ADR,
not three independent bumps.
