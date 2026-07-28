# AgriCore project overview and product requirements

**Status:** v1.0.1 source-release preparation manifest; no v1.0.1 tag,
GitHub Release, package-publication result, or hosted production deployment is
claimed

**Release provenance:** [Prepared v1.0.1 release manifest](releases/v1.0.1.md)
defines the required source-tag gate, immutable SHA packages, and operator
deployment boundary. The [v1.0.0 manifest](releases/v1.0.0.md) is the
historical predecessor record.

## Product problem

Agricultural enterprises often split farm maps, crop plans, field work, material
stock, harvests, sensor alerts, sales, and origin evidence across spreadsheets
and disconnected tools. That makes stock correctness, audit history, farm-level
access, and public traceability difficult to prove.

AgriCore provides one operational platform while keeping each business capability
inside an independently owned service and database.

## Users

| Persona | Primary outcomes |
|---|---|
| System administrator | Identity, role/permission policy, deployment, audit |
| Farm manager | Enterprise, farm, plot, cycle, work, harvest oversight |
| Agronomist | Crop requirements, observations, task and IoT decisions |
| Field worker | Assigned task execution and evidence attachment |
| Warehouse manager | Batch stock, reservations, movements, harvest receipt |
| Sales staff | Customers, orders, inventory saga status |
| Auditor | Read-only operational and traceability evidence |
| Produce consumer | Public QR origin information without internal data |

## Functional requirements

1. Authenticate with short-lived RS256 access tokens and rotated opaque refresh
   tokens.
2. Scope protected farm data by role, permission, and authoritative farm
   membership.
3. Manage enterprise/farm/area/plot/soil/irrigation and agronomic catalog data.
4. Track crop cycles, observations, stage history, work assignments, execution,
   materials, and private image evidence.
5. Complete farm-scoped harvest batches and project authoritative farm events
   idempotently into Inventory and Traceability.
6. Keep stock movements and expiry-aware lots; prevent negative and duplicate
   mutations under concurrency.
7. Ingest authenticated MQTT telemetry, enforce per-device admission quotas,
   deduplicate readings, evaluate versioned thresholds, suppress alert storms,
   and detect offline devices.
8. Orchestrate farm-scoped Sales inventory reservations without a distributed
   transaction.
9. Publish `UserRegistered.v1` atomically with Identity registration and consume
   it as an idempotent welcome-email intent. Persist email and in-app delivery
   outcomes instead of reporting attempted sends as successful; external
   automatic delivery is at-most-once, while local `IN_APP` recovery is safely
   retryable. Provide an authorized administrative inbox.
10. Publish a public-safe QR read model without cross-service database queries.
11. Provide an authenticated, persisted, read-only assistant with replayable SSE,
    bounded tools, opt-in curated cited retrieval, budgets, and safe
    provider-unavailable behavior.
12. Provide an accessible React operations console and reproducible local demo
    data/media.

## Non-functional requirements

- Java 21, Spring Boot, PostgreSQL/Flyway, Redis, Kafka, MQTT, MinIO-compatible
  object storage, and a React/TypeScript console.
- Database per service; REST for immediate decisions and Kafka for implemented
  domain events.
- Transactional outbox for producers and persistent idempotency for consumers.
- External notification ambiguity becomes
  `FAILED`/`DELIVERY_OUTCOME_UNKNOWN`; recovery must not automatically resend a
  message the provider may already have accepted.
- Optimistic or pessimistic locking selected from the actual contention
  invariant.
- Versioned OpenAPI, AsyncAPI, and JSON Schema contracts.
- Structured logs, metrics, traces, health probes, bounded retries, DLT repair,
  and no committed secret.
- Explicit assistant retention/cleanup controls; telemetry deletion remains
  unset until a product-owned retention horizon and storage budget are approved.
- Assistant RAG defaults off. Its V5-compatible `KNOWLEDGE` evidence is
  persisted, so production adoption requires a compatible-image rollout with
  retrieval disabled before the intentionally unavailable `Recreate` activation.
  Disabling retrieval later is not a safe binary downgrade.
- Docker Compose for local evidence and a hardened Helm application chart for
  operator-provided clusters.
- Focused conventional commits and reproducible verification from a clean
  revision.

## Release acceptance

- Every mandatory capability has direct implementation, contract, migration,
  test, runtime, or workflow evidence.
- Full Maven, frontend, browser, Compose, Helm, secret, dependency, and container
  gates pass.
- A gateway JWT path and broker-backed harvest projection are reproducible.
- The bounded Large dataset is idempotent at 32 farms, 768 plots, 32
  production flows, 128 work tasks, 640 readings, and 16 confirmed sales
  orders, with repository-owned media stored through the Work attachment
  boundary.
- Platform docs, service-local README files, diagrams, generated clients, and
  environment examples match the released revision.
- RAG activation evidence records the compatible V5-or-newer image rollout with
  retrieval disabled, absence of old Assistant Pods, healthy readiness, and the
  completed V5 migration before `assistant.ragEnabled=true` triggers the
  intentional `Recreate` interruption. Any pre-V5 rollback follows the
  documented drain, backup, and evidence-neutralization procedure.
- Docker Hub and GitHub Packages publish immutable SHA images only after default
  branch CI succeeds. A v1.0.1 source release must be resolved through its
  later release record to full/short SHA tags, signatures, SBOM, and
  provenance; deployments pin the resulting digest. The prepared manifest is
  not that release record.
- Production operators explicitly supply secrets, TLS, database backups, Kafka
  authorization, storage, SMTP, and observability retention.

## Out of scope for this repository

- A hosted production cluster or managed infrastructure account.
- Autonomous assistant writes or arbitrary external URL access.
- Cross-service database joins and distributed ACID transactions.
- Product-specific telemetry retention, legal certification authority, tax, and
  invoice compliance policies without an accepted business decision.

See [the roadmap](project-roadmap.md) for evidence status and
[system architecture](architecture/SYSTEM_ARCHITECTURE.md) for boundaries.
