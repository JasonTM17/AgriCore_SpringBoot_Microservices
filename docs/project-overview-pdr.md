# Project Overview (PDR)

## Problem

Commercial crop production is tracked across disconnected tools: plots in spreadsheets, field work on
paper or chat, harvest weights in a warehouse ledger, sales in another system, and nothing tying a
delivered box back to the plot it grew on. The cost shows up as unverifiable provenance claims, stock
that does not match reality, and no audit trail when a buyer asks where produce came from.

AgriCore models the whole chain — farm → plot → crop cycle → field work → harvest → inventory → sale —
in one platform, with a public traceability record as the visible output.

## Users and roles

Seven roles ship seeded; new registrations receive `FIELD_WORKER` and are promoted by an admin.

| Role | Uses the platform to |
|------|----------------------|
| `SYSTEM_ADMIN` | Manage users and role assignments |
| `FARM_MANAGER` | Own farms, plots, and crop cycle planning |
| `AGRONOMIST` | Drive cycle stages and field work decisions |
| `FIELD_WORKER` | Execute and complete assigned tasks |
| `WAREHOUSE_MANAGER` | Receive harvests, manage stock and reservations |
| `SALES_STAFF` | Manage customers and orders |
| `AUDITOR` | Read-only review across domains |

Plus an unauthenticated consumer who scans a QR code and sees a deliberately narrow public trace.

## Product decisions

- **Traceability is the differentiator.** The public read model is a first-class projection maintained
  from harvest events, not a report generated on demand.
- **Inventory truth beats convenience.** Stock changes flow from harvest events and reservation
  confirmation, so a sale can never silently exceed physical stock. A crash between reserve and confirm
  is recoverable through an explicit reconcile action rather than manual row edits.
- **Provenance data is public; everything else is not.** The QR response exposes farm name, plot code,
  product, and harvest data — never internal ids, prices, customers, or users.
- **Registration is closed by default in production.** Open self-registration exists for local demos
  only; production creates users through an admin.

## Architecture commitments

Database per service, Kafka domain events through a transactional outbox, idempotent consumers with
dead-letter topics, RS256 JWT with JWKS verified locally by each service, no service registry, no
shared domain library. Rationale per decision lives in [the ADRs](adr/); the runtime picture is in
[System Architecture](architecture/SYSTEM_ARCHITECTURE.md).

## Scope boundaries

**In scope:** crop production operations, harvest-to-stock flow, order-to-reservation flow, device and
sensor reading capture, public traceability, role-based access.

**Out of scope (deliberately):**

- Accounting, payroll, invoicing, tax.
- Logistics, route planning, fleet.
- Marketplace, buyer-side commerce, payments.
- Agronomic prediction and yield modelling.
- Native mobile applications — responsive web only.

## What "done" means

The platform is judged by an end-to-end path, not by feature count: register → create farm and plot →
start a crop cycle → assign and complete field work → complete a harvest → stock appears in inventory
via Kafka → a public QR code returns that batch's provenance. `scripts/verify-platform.ps1` runs
exactly this path and writes an evidence bundle.

## Non-functional expectations

- Every service: own database, Flyway migrations, `/actuator/health`, `/actuator/prometheus`.
- Cross-service writes never dual-write: database and event commit in one transaction.
- Duplicate event delivery must not double-count stock.
- No secrets in git; security-relevant configuration defaults to fail-closed.
- Container images: multi-stage build, non-root user, healthcheck, published only from a
  CI-verified commit.

Current gaps and their reasons are tracked in [project-roadmap.md](project-roadmap.md).
