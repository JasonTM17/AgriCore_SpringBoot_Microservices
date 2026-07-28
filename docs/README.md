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
- [Developer workflows](developer-workflows.md) — focused verification for
  console, service, contract, and full-stack changes.
- [Deployment guide](deployment-guide.md) — operator-owned cluster inputs and
  immutable-image deployment.
- [v1.0.0 release manifest](releases/v1.0.0.md) — source-release criteria,
  package resolution, verification, and operator boundary.
- [Evidence register](evidence/README.md) — current source-release provenance
  and qualified historical records.

## Reference

- Contracts: [OpenAPI](../contracts/openapi/),
  [AsyncAPI](../contracts/asyncapi/), and
  [event schemas](../contracts/event-schemas/).
- [Architecture decisions](adr/README.md)
- [Service dependencies](diagrams/service-dependencies.md)
- [Assistant RAG trust boundary](diagrams/assistant-rag-trust-boundary.md)
- [Release provenance evidence chain](diagrams/release-provenance.md)
- [Kafka retry and DLT runbook](runbooks/kafka-dlq.md)
- [Assistant RAG operations](runbooks/assistant-rag-operations.md)
- [Security review](security/SECURITY_REVIEW.md)
- [Codebase summary](codebase-summary.md)
- [Roadmap](project-roadmap.md)
