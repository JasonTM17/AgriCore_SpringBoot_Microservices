# AgriCore code standards

## Design order

Use YAGNI, then KISS, then DRY. Extend an existing boundary before creating a
generic framework. Split files above roughly 200 lines when a real responsibility
boundary exists; configuration, migrations, scripts, and prose are exceptions.

## Java and Spring

- Target Java 21 and use constructor injection.
- Controllers own HTTP mapping, Bean Validation, authentication context, and
  response codes. Business transitions belong in application/domain services.
- Transactions end before remote calls. Never keep a database transaction open
  across another service or broker.
- Use typed exceptions and the shared API error envelope; do not return stack
  traces or raw downstream bodies.
- Use `BigDecimal` for quantities and prices. State the scale and rounding policy
  at the domain boundary.
- Normalize business codes once and enforce matching database uniqueness.
- Use UTC `Instant` for event timestamps and audit moments; use `LocalDate` only
  for calendar dates without time-of-day meaning.
- Keep security enabled by default. Development headers require the explicit
  dev-mode flag and remain off in Compose/Helm application profiles.

## Persistence

- One service owns one database and Flyway history.
- Migrations are immutable after merge. Add expand/backfill/contract migrations
  for existing data and document non-trivial rollback.
- Add indexes for foreign keys, queue scans, frequent filters, and idempotency
  keys; verify with PostgreSQL tests when locking or SQL dialect matters.
- Every mutable aggregate has an explicit concurrency policy. Do not use locking
  as decoration.
- Inventory quantity changes always produce a movement with business reference.
- Store credentials only as strong hashes where recovery is not required.

## APIs and events

- Version public contracts and keep runtime, gateway, generated clients, and docs
  synchronized.
- Validate unknown JSON fields when silent acceptance could hide contract drift.
- Use page/size bounds for collections; sorting fields are allowlisted.
- All domain events use the shared envelope and immutable schema version.
- Producers commit an outbox row with the aggregate change. Pollers use bounded
  batches, row locks, send timeouts, backlog metrics, and repairable failures.
- Consumers validate exact type/version, commit side effect plus processed marker,
  and route invalid/exhausted records through the documented DLT path.

## TypeScript and React

- Keep strict TypeScript; do not add `any` without a narrow documented boundary.
- Use generated OpenAPI types and React Query for server state.
- Every route handles loading, empty, error, unauthorized, and stale mutation
  outcomes.
- Forms use persistent labels, field errors, keyboard focus, and disabled/pending
  states. Do not rely on placeholder text as a label.
- Lazy-load route modules and non-critical images. Give media fixed aspect ratios,
  meaningful alt text, a broken-image fallback, and width-based `srcset`/`sizes`
  variants.
- Serialize login behind an active logout and invalidate stale refresh results
  with a session generation/epoch. A late authentication response must never
  restore a cleared or replaced session.
- Backend authorization remains authoritative; hiding a control is only a user
  experience aid.

## Tests

- Unit-test policies, canonicalization, and failure classification.
- Integration-test controllers, transactions, migrations, authorization, and
  persistence invariants.
- Use PostgreSQL/TimescaleDB Testcontainers for dialect, locking, index, or
  migration behavior; H2 is not evidence for those properties.
- Test duplicate messages, concurrent mutations, cross-farm UUID guessing,
  timeouts, compensation, and partial downstream failures.
- Contract drift, lint, typecheck, unit tests, production build, and critical
  browser journeys are release gates.
- Never weaken an assertion or disable a check to make CI green.

## Security and operations

- Never log passwords, bearer/refresh tokens, cookies, provider output containing
  secrets, or private object URLs.
- Secrets come from local ignored environment files or deployment secret stores.
- Bound request size, collection size, retries, timeouts, queues, log rotation,
  and cleanup.
- Emit trace/correlation identifiers, metrics for backlog/terminal outcomes, and
  actionable health signals without high-cardinality labels.

## Git

- Branch names describe intent: `feature/...`, `fix/...`, `release/...`.
- Commits are conventional, focused, and contain no automated-author references.
- Preserve unrelated user changes and never rewrite shared history without
  explicit approval.
