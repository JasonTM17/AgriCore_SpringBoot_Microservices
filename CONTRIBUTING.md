# Contributing to AgriCore

Thanks for taking a look. This is a portfolio project, so the bar is "a reviewer should be able to
read the diff and believe it" rather than "ship fast".

## Prerequisites

- JDK 21+ (the compile target is 21 — `maven.compiler.release=21` stays even on a newer JDK)
- Maven 3.9+ (or the bundled `./mvnw`)
- Docker / Docker Compose, for the infrastructure stack and integration tests

## Getting set up

```bash
# 1. Infrastructure: PostgreSQL (5434), Redis (6380), Kafka (9092), Kafka UI (8088)
./scripts/dev-up.sh          # Linux/macOS
.\scripts\dev-up.ps1         # Windows

# 2. JWT keys for identity-service (once)
.\scripts\generate-jwt-keys.ps1

# 3. Build everything
./mvnw verify
```

Integration tests need the compose Postgres running. `./mvnw test` on its own will fail on the
integration suites if infrastructure is down.

## Working on a change

Run the narrowest useful test first, then broaden:

```bash
# One module. -am builds its dependencies, which you need because
# common-lib/common-security are not installed to ~/.m2.
./mvnw -B -am -pl services/identity-service test

# Several modules
./mvnw -B -am -pl services/identity-service,services/notification-service test

# Everything (what CI runs)
./mvnw -B verify
```

`verify` writes a JaCoCo report per module to `target/site/jacoco/`.

## Coverage

Coverage is **measured but not enforced** — the CI gate is advisory (`continue-on-error`) while the
baseline is brought up. Do not let a change lower coverage on a module you touched. Target
thresholds, for when the gate flips strict:

| Module type | Lines | Branches |
|-------------|-------|----------|
| Services | ≥ 70% | ≥ 65% |
| Critical (identity, sales) | ≥ 90% | ≥ 85% |

## Commit messages

[Conventional Commits](https://www.conventionalcommits.org/), enforced by review:

```
<type>(<scope>): <subject>
```

- Types: `feat`, `fix`, `docs`, `style`, `refactor`, `perf`, `test`, `build`, `ci`, `chore`, `revert`
- Scope is the service or area: `feat(identity):`, `fix(harvest):`, `ci(publish):`
- Subject ≤ 72 chars, imperative mood, no trailing period
- Breaking changes: `feat(api)!:` or a `BREAKING CHANGE:` footer
- The body explains **why**, wrapped at 100 columns

Example from this repo's history:

```
feat(identity): publish UserRegistered through transactional outbox

The outbox_events table shipped in V1 but nothing wrote to it. Registration now
enqueues a UserRegistered.v1 envelope in the same transaction as the user insert,
and a polling publisher ships it to agricore.identity.events.
```

## Pull requests

`main` is protected. Open a PR from a branch; these checks must be green before merge:

| Check | What it runs |
|-------|--------------|
| `ci / build-test` | `./mvnw -B verify` across the full reactor |
| `ci / secret-scan` | Gitleaks over the diff |
| `ci / compose-config` | `docker compose config` on all compose files |
| `codeql` | SAST over Java sources |
| `trivy` | Filesystem and dependency vulnerability scan |

Keep a PR to one logical change. If a diff mixes a refactor with a behaviour change, split it.

## Architecture rules

These are load-bearing — a PR that breaks one will be sent back:

- **Database per service.** No cross-service JPA relationships, no shared schema. Services
  reference each other's aggregates by id only.
- **`libs/common-lib` stays framework-free.** An ArchUnit test fails the build if anything under
  `com.agricore.common..` gains a `org.springframework..` dependency.
- **Events go through the transactional outbox.** Do not publish to Kafka directly from an
  application service. See [`docs/adr/0004-transactional-outbox-polling.md`](docs/adr/0004-transactional-outbox-polling.md).
- **Consumers are idempotent.** Every listener records a processed-event marker in the same
  transaction as its side effect. See [`docs/adr/0005-idempotent-consumer.md`](docs/adr/0005-idempotent-consumer.md).
- **Contracts are canonical.** Changing a REST route or event payload means updating
  `contracts/openapi/` or `contracts/asyncapi/` in the same PR.

An architectural decision that is not obvious from the code needs an ADR in `docs/adr/`, using
[`docs/adr/template.md`](docs/adr/template.md). ADRs are append-only: supersede, never edit an
accepted one.

## Things that will not be merged

- Secrets, dotenv files, keys, or credentials in the diff — even in tests or examples
- A new service without a `README.md`, a healthcheck endpoint, and a Dockerfile
- Documentation that describes behaviour the code does not implement
- Commented-out code, or a `TODO` without an issue behind it
