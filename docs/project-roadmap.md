# AgriCore roadmap

**Last updated:** 2026-07-26

This roadmap records evidence gates, not delivery dates. A capability moves to
complete only after code, contract, migration, tests, operations, and docs agree.

## Release 1.0 completion

| Area | Evidence state |
|---|---|
| Identity, JWT, roles, refresh security | Implemented; canonical permission guards and administration covered by tests |
| Farm, catalog, crop-cycle, work | Core vertical slices implemented with farm boundary, optimistic/history evidence, and PostgreSQL crop-cycle overlap exclusion |
| Work image evidence | Private MinIO-compatible storage and validated attachments implemented |
| Harvest, inventory, traceability | Authoritative farm-scoped events/contracts, broker-backed projections, duplicate protection, expiry-aware lot allocation, and DLT recovery implemented |
| MQTT and IoT alerts | Authenticated ingestion, per-device admission quotas, idempotency, cooldown, offline flow, and TimescaleDB migration implemented |
| Sales saga | Farm-scoped reservation/compensation, bounded timeout, durable retry recovery, fulfillment milestones, and contract fields implemented |
| Notification | Requested/sent/failed truth, SMTP and persisted in-app adapters, administrative inbox endpoints, invalid-payload DLT handling, and idempotent event consumption implemented |
| Assistant | Persisted read-only boundary, budgets, SSE replay, safe provider-none behavior implemented |
| Console | Core workflows, assistant, Inventory, Sales, IoT, identity administration, permission-aware navigation, serialized auth/logout transitions, and responsive media variants implemented |
| Platform | Compose and Helm tenant dependencies, read-only application filesystems, gateway Service alias, configurable egress policy, observability, security workflows, and signed SHA-only dual-registry publishing implemented; final clean-revision and remote execution pending |
| Docs/demo | Repository media/GIF, regional seed, bounded cross-domain dataset, diagrams, ADRs, and release docs synchronized |

Current checkpoint: implementation/remediation complete. Final pre-landing
review, clean-revision gates, GitHub CI, merge, registry publication, and
post-publication digest verification remain open.

## Post-1.0 candidates

These items require a product or operations decision before implementation:

- Raw and aggregate telemetry retention, compression, and sustained ingestion
  service-level objectives.
- Packaging/certification authority and public-redaction policy.
- Sales tax, discounts, currency, invoice compliance, carrier integration, and
  payment lifecycle.
- Production multi-region topology, disaster recovery objectives, and managed
  secret rotation.
- Assistant retrieval ingestion or write actions with explicit confirmation,
  policy, audit, and compensation.
- Analytics/warehouse pipeline for cross-service historical reporting.

## Release policy

- No critical/high accepted security finding.
- No required capability documented as present without executable evidence.
- No force merge, secret bypass, skipped test, or mutable-only deployment tag.
- Known operator responsibilities remain explicit in the deployment guide and
  release record.
