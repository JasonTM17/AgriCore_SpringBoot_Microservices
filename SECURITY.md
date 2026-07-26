# Security Policy

## Supported versions

AgriCore has no tagged release yet. Security fixes land on `main`, and published images
(`nguyenson1710/agricore-*`) are rebuilt from `main` on every push.

| Version | Supported |
|---------|-----------|
| `main` / `latest` images | Yes |
| Any older image SHA | No — pull `latest` or the current commit SHA |

## Reporting a vulnerability

Report privately. **Do not open a public issue for a security bug.**

- Preferred: [GitHub private vulnerability reporting](https://github.com/JasonTM17/AgriCore_SpringBoot_Microservices/security/advisories/new)
- Alternative: email `jasonbmt06@gmail.com` with `[AgriCore security]` in the subject

Please include the affected service, the commit SHA or image tag, reproduction steps, and the
impact you believe it has. A proof-of-concept helps but is not required.

This is a portfolio project maintained by one person, not a commercial product with an on-call
rotation. Expect a first response within about a week. There is no bug-bounty payout.

## Scope

In scope — anything under `services/`, `libs/`, `contracts/`, `infrastructure/`, and the GitHub
Actions workflows.

Out of scope:

- The demo credentials and keys in `.env.example` and `scripts/generate-jwt-keys.*`. These are
  development scaffolding; every deployment is expected to generate its own.
- Findings that require an attacker to already hold the JWT signing key or database credentials.
- The default `docker-compose.yml` binding services to localhost ports. It is a development stack,
  not a hardened deployment. See [`docs/deployment-guide.md`](docs/deployment-guide.md). In
  particular, identity is published directly on `8081` there, so the gateway can be bypassed and the
  forwarded-address header spoofed; the per-account lockout still applies. Recorded in
  [`docs/project-roadmap.md`](docs/project-roadmap.md).

## Automated scanning

Every push and pull request runs:

| Check | Workflow | Scope |
|-------|----------|-------|
| Secret scan | `.github/workflows/ci.yml` (Gitleaks) | Full history diff |
| SAST | `.github/workflows/codeql.yml` (CodeQL) | Java sources |
| Vulnerability scan | `.github/workflows/trivy.yml` (Trivy) | Filesystem + dependencies |
| Dependency updates | `.github/dependabot.yml` | Maven, GitHub Actions, Docker |

Image publication is gated on `ci` succeeding on the default branch, so a failing security check
blocks the release of a new image.

## Security posture

Implemented and verifiable in source:

- **Asymmetric JWT.** RS256 with a JWKS endpoint; verifiers validate locally rather than calling
  identity per request. See [`docs/adr/0003-jwt-rs256-jwks.md`](docs/adr/0003-jwt-rs256-jwks.md).
- **Audience validation.** Access tokens with a wrong or missing `aud` are rejected.
- **Refresh token rotation.** Refresh tokens are opaque and stored hashed, never in plaintext.
- **Login rate limiting and account lockout** on the identity service. The two are paired
  deliberately: the per-IP limiter alone does not stop a distributed attack on one account, and the
  per-account lockout alone does not stop credential stuffing across many accounts.
- **Database per service.** No shared schema and no cross-service JPA relationships, so a
  compromise of one service does not hand over another's data.
- **Non-root containers** with pinned base images.

Known gaps, tracked rather than hidden — see [`docs/project-roadmap.md`](docs/project-roadmap.md):

- Coverage thresholds are measured but not yet enforced in CI.
- The JWT signing key is loaded from a mounted PEM; there is no automated rotation schedule.
