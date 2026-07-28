# Release closeout — 2026-07-28

## Scope

- Verified repository revision:
  `a7568aec382186849635b48d2d0f22f5bf565ea2` on `main`.
- Merge record: [pull request #51](https://github.com/JasonTM17/AgriCore_SpringBoot_Microservices/pull/51).
- CI evidence: [default-branch CI run 30326755192](https://github.com/JasonTM17/AgriCore_SpringBoot_Microservices/actions/runs/30326755192).
- Package evidence: [Docker Publish run 30327148247](https://github.com/JasonTM17/AgriCore_SpringBoot_Microservices/actions/runs/30327148247).

This record proves a verified repository revision and immutable package
publication. It does not claim a SemVer release, a hosted production deployment,
production TLS/mTLS, Kafka ACLs, backups, or operator retention policy.

## Verified gates

| Gate | Result | Evidence |
|---|---|---|
| Default-branch CI | Passed | CI run 30326755192 completed successfully for the verified revision |
| Docker publication | Passed | Docker Publish run 30327148247 completed successfully after retry, with 44/44 jobs successful |
| Image promotion | Passed | All 14 images received short- and full-SHA tags in Docker Hub and GHCR; registry digest parity was checked |
| Backend | Passed | Full Maven reactor completed against the integrated revision; Testcontainers coverage requires Docker when reproduced locally |
| Console | Passed | Contracts, lint, typecheck, unit tests, production build, and Playwright journeys completed before merge |
| Documentation/media | Passed | Local Markdown targets resolve; console captures and showcase-media provenance are repository-owned and verified |

The package workflow promotes only immutable short- and full-SHA tags. It does
not promote `latest`. Verify a candidate by SHA tag when locating it, then deploy
the exact registry digest through the Helm chart.

## Operator handoff

Before a production deployment, the operator must provide managed database,
Kafka authorization/retention, MQTT TLS/ACLs, object storage, SMTP, secrets,
Ingress/TLS, backups, observability retention, and incident procedures. See the
[deployment guide](../deployment-guide.md) and the
[Kafka retry/DLT runbook](../runbooks/kafka-dlq.md).

## Historical evidence

The [2026-07-26 evidence](release-verification-2026-07-26.md) remains a
qualified snapshot for commit `5867b37`; it is useful for local runtime detail
but is not evidence for the verified revision above. Its disposable host-local
capture bundle was intentionally cleaned after this closeout.
