# AgriCore roadmap

**Last updated:** 2026-07-26

This roadmap records evidence gates, not delivery dates. A capability moves to
complete only after code, contract, migration, tests, operations, and docs agree.

## Release 1.0 completion

| Area | Evidence state |
|---|---|
| Identity, JWT, roles, refresh security | Implemented; canonical permission guards and administration covered by tests |
| Farm, catalog, crop-cycle, work | Core vertical slices implemented with farm boundary and optimistic/history evidence |
| Work image evidence | Private MinIO-compatible storage and validated attachments implemented |
| Harvest, inventory, traceability | Broker-backed projections, duplicate protection, expiry-aware lot allocation, and DLT recovery implemented |
| MQTT and IoT alerts | Authenticated ingestion, idempotency, cooldown, offline flow, and TimescaleDB migration implemented |
| Sales saga | Reservation/compensation, bounded timeout, durable retry recovery, fulfillment milestones, and contract fields implemented |
| Notification | Requested/sent/failed truth, SMTP adapter, idempotent event consumption implemented |
| Assistant | Persisted read-only boundary, budgets, SSE replay, safe provider-none behavior implemented |
| Console | Core workflows, assistant, Inventory, Sales, IoT, identity administration, and permission-aware navigation implemented |
| Platform | Compose, Helm, observability, security workflows, signed dual-registry publishing implemented; final clean-revision verification pending |
| Docs/demo | Repository media/GIF, regional seed, bounded cross-domain dataset, diagrams, ADRs, and release docs synchronized |

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
