# AgriCore project overview and product requirements

**Status:** Active pre-release

**Last verified:** 2026-07-23

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
5. Complete harvest batches and project events idempotently into inventory and
   traceability.
6. Keep stock movements and expiry-aware lots; prevent negative and duplicate
   mutations under concurrency.
7. Ingest authenticated MQTT telemetry, deduplicate readings, evaluate versioned
   thresholds, suppress alert storms, and detect offline devices.
8. Orchestrate sales inventory reservations without a distributed transaction.
9. Persist notification delivery outcomes instead of reporting attempted sends as
   successful.
10. Publish a public-safe QR read model without cross-service database queries.
11. Provide an authenticated, persisted, read-only assistant with replayable SSE,
    bounded tools, budgets, and safe provider-unavailable behavior.
12. Provide an accessible React operations console and reproducible local demo
    data/media.

## Non-functional requirements

- Java 21, Spring Boot, PostgreSQL/Flyway, Redis, Kafka, MQTT, MinIO-compatible
  object storage, and a React/TypeScript console.
- Database per service; REST for immediate decisions and Kafka for implemented
  domain events.
- Transactional outbox for producers and persistent idempotency for consumers.
- Optimistic or pessimistic locking selected from the actual contention
  invariant.
- Versioned OpenAPI, AsyncAPI, and JSON Schema contracts.
- Structured logs, metrics, traces, health probes, bounded retries, DLT repair,
  and no committed secret.
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
- Docs, diagrams, generated clients, and environment examples match the released
  revision.
- Docker Hub and GitHub Packages publish immutable SHA images only after default
  branch CI succeeds; signatures, SBOM, and provenance are verifiable.
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
