# 11. Maven and pnpm monorepo

**Date:** 2026-07-23

**Status:** Accepted

## Context

AgriCore contains 13 Spring applications, three shared Java libraries, a React
console, contracts, deployment assets, and cross-service verification scripts.
They change together while each service must remain independently buildable and
deployable.

## Decision

Keep application, library, contract, infrastructure, and documentation source in
one Git repository.

- Maven reactor modules own Java compilation and dependency alignment.
- pnpm workspaces own the console and contract-generation tooling.
- Services keep independent Dockerfiles, Flyway histories, runtime configuration,
  and image names.
- Shared libraries contain only cross-cutting contracts or adapters; no service
  shares JPA entities or another service's database model.
- CI runs complete repository gates for each eligible revision; it does not
  claim path-based changed-artifact detection.

## Consequences

### Positive

- One revision identifies a compatible set of code, contracts, charts, and docs.
- Cross-service refactors and generated-client drift are reviewed atomically.
- Local Compose and full-platform verification remain reproducible.

### Negative

- A full reactor build is slower than an isolated service build.
- Repository access grants visibility to all service source.
- Ownership and path filters require discipline as the team grows.

### Trade-offs

The repository accepts a larger CI graph and broader checkout in exchange for
contract consistency and simpler release evidence. Independent runtime images
preserve deployment autonomy without multiplying repository coordination.

## Alternatives considered

- **Repository per service:** rejected for the current team because contract,
  chart, and end-to-end changes would require coordinated multi-repository
  releases.
- **Single deployable modular monolith:** rejected because the requested service
  boundaries, database ownership, and independent scaling are explicit product
  constraints.
- **Git submodules for shared contracts:** rejected because update ordering and
  detached revisions make local onboarding and atomic reviews harder.

## References

- [Parent Maven build](../../pom.xml)
- [pnpm workspace](../../pnpm-workspace.yaml)
- [CI workflow](../../.github/workflows/ci.yml)
- [Project layout](../../README.md#project-layout)
