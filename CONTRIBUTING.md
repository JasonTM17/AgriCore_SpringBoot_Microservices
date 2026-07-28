# Contributing to AgriCore

AgriCore accepts focused changes that preserve service ownership, public
contracts, and operational evidence.

## Before changing code

1. Read [the project overview](docs/project-overview-pdr.md), [architecture](docs/architecture/SYSTEM_ARCHITECTURE.md),
   and [code standards](docs/code-standards.md).
2. Create an intent-based branch such as `feature/inventory-transfer` or
   `fix/harvest-event-deduplication`.
3. For cross-module or risky work, add a plan under
   `plans/<timestamp>-<descriptive-slug>/`.
4. Check the relevant OpenAPI, AsyncAPI, event schema, migration history, and
   neighboring tests before editing.

## Change rules

- Keep database ownership inside one service. Never query another service's
  schema or share JPA entities.
- Preserve backward compatibility unless the accepted change explicitly versions
  the contract.
- Add Flyway migrations; never edit or delete an applied migration.
- Validate external input at the controller, message, or storage boundary.
- Keep authentication and authorization at domain services even when the gateway
  also rejects unauthenticated traffic.
- Do not commit `.env`, tokens, private keys, provider keys, registry credentials,
  production data, or personal information.
- Generate clients from contracts; do not hand-edit files under
  `apps/agricore-console/src/lib/api/generated/`.

## Verification

Run the narrowest affected test first, then the shared gates when a public or
cross-service contract changes.

```bash
./mvnw -pl services/<service> -am test
./mvnw -B verify

pnpm contracts:check
pnpm lint
pnpm typecheck
pnpm test
pnpm build
```

Infrastructure changes also require:

Before local Compose validation, complete the root [Quick start](README.md#quick-start):
copy `.env.example` to `.env`, set a local `AGRICORE_CLIENT_IP_SIGNING_SECRET`,
and generate the local JWT keys. Do not use a literal secret from documentation
or commit the resulting local configuration or keys.

```bash
docker compose config --quiet
helm lint infrastructure/helm/agricore \
  --set global.imageTag=0000000000000000000000000000000000000000 \
  --set global.requireImageDigest=false
helm template agricore infrastructure/helm/agricore \
  --set global.imageTag=0000000000000000000000000000000000000000 \
  --set global.requireImageDigest=false > /dev/null
```

The chart deliberately rejects unpinned production images. A real installation
also requires the Inventory internal credential Secret; see the
[chart README](infrastructure/helm/agricore/README.md) for the full values
contract. The commands above use CI-safe placeholder values for lint/render
only.

Browser journeys use `pnpm --filter @agricore/console e2e`. Full-stack evidence
uses `scripts/verify-platform.ps1` or `scripts/verify-platform.sh`.

Showcase-media changes also require:

```bash
node scripts/verify-showcase-media.mjs
```

## Commits and pull requests

- Use focused conventional commits: `feat`, `fix`, `refactor`, `test`, `docs`,
  `ci`, `build`, or `perf`.
- Do not mention automated tooling or assistants in commit messages.
- Separate migrations, behavior, generated contracts, and unrelated cleanup when
  they can be reviewed independently.
- Explain the problem, trust boundaries, migration/rollback path, and verification
  evidence in the pull request.
- A pull request is ready only when required CI, security, contract, Compose, and
  Helm checks pass with no hidden failures.

## Release provenance

Version releases use an annotated source tag and a GitHub Release only after the
merged target's required workflows finish. Do not create a SemVer container tag:
the publish workflow promotes immutable short- and full-SHA image tags and the
operator deploys a resolved digest. A release record links the source target to
the matching CI/package evidence; it does not represent a production deployment.
Pushing a stable `vX.Y.Z` annotated tag invokes
[`release-provenance.yml`](.github/workflows/release-provenance.yml), which
refuses to create the GitHub Release unless the tag target is the current default
branch tip with successful `ci` and `docker-publish` runs for that same SHA.

## Reporting security issues

Do not open a public issue containing an exploitable vulnerability, credential,
or production data. Use the repository's private security-advisory channel. If
that channel is unavailable, contact the repository owner privately and include
only the minimum reproduction needed.
