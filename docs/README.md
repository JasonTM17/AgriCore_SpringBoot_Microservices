# AgriCore documentation

This directory is the repository source of truth for architecture, operations,
security boundaries, and release evidence. Runtime code, versioned contracts,
and deployed configuration remain authoritative when they differ.

## Start here

- [Project overview and PDR](project-overview-pdr.md) — product scope and
  acceptance boundaries.
- [System architecture](architecture/SYSTEM_ARCHITECTURE.md) — service,
  security, integration, and deployment design.
- [Local operations](runbooks/local-operations.md) — Compose setup, demo data,
  and runtime checks.
- [Deployment guide](deployment-guide.md) — operator-owned cluster inputs and
  immutable-image deployment.
- [v1.0.0 release notes](releases/v1.0.0.md) — source-release scope, package
  resolution, verification, and operator boundary.
- [Historical 2026-07-28 closeout](evidence/release-closeout-2026-07-28.md) —
  verified predecessor commit and package evidence.

## Reference

- [Architecture decisions](adr/README.md)
- [Service dependencies](diagrams/service-dependencies.md)
- [Kafka retry and DLT runbook](runbooks/kafka-dlq.md)
- [Assistant RAG operations](runbooks/assistant-rag-operations.md)
- [Security review](security/SECURITY_REVIEW.md)
- [Codebase summary](codebase-summary.md)
- [Roadmap](project-roadmap.md)
