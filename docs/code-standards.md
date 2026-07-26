# Code Standards

Conventions this repository actually follows. Deviations are bugs, not style preferences.

## Language and build

- Java 21 (`maven.compiler.release=21`). The host JDK may be newer; the release target is not raised
  to match it.
- Maven multi-module reactor; every service and library is a module in the root `pom.xml`.
- Dependency versions are declared **only** in the root `pom.xml` (`dependencyManagement` +
  properties). Service poms never carry a `<version>`.
- Two version pins are security decisions, not upgrade laziness — do not bump them casually:
  `spring-boot.version` (Actuator CVEs) and `spring-kafka.version` (deserialization CVE). Both carry
  an explanatory comment at the declaration site.

## Package structure

```text
com.agricore.<service>
  api/            controllers + request/response records
  application/    application services, outbox writers
  domain/         entities, value objects, enums, domain exceptions
  infrastructure/ persistence, messaging, security, configuration
```

- Controllers hold no business logic — validate, delegate, map.
- Application services orchestrate; they do not build event JSON (that is an outbox writer's job).
- Domain code has no Spring or JPA imports where avoidable, and never imports another service.
- An ArchUnit test in `libs/common-lib` enforces that the shared library stays framework-free: no
  class under `com.agricore.common..` may depend on `org.springframework..`. Per-service hexagonal
  rules are not yet enforced by tests.

## Modularization

- A file past ~200 lines is a signal to extract a collaborator. `CropCycleOutboxWriter` and
  `IdentityOutboxWriter` exist for exactly this reason.
- Extract by responsibility, not by line count: envelope construction, persistence mapping, policy
  checks are natural seams.

## Naming

- Services are named for responsibility, never for implementation technology (`identity-service`, not
  `auth-java`).
- Event types are `PascalCase.vN` string constants in `EventTypes` — `UserRegistered.v1`. The version
  suffix is part of the name.
- Topics are `agricore.<domain>.events`; dead-letter topics are `<topic>.DLT`.
- Kebab-case for file and directory names outside Java; Java keeps PascalCase types.
- Database identifiers are `snake_case`; JPA maps explicitly with `@Column(name = ...)`.

## Persistence

- Database per service. No cross-service foreign keys, no shared schema, no cross-service joins.
- Flyway migrations are append-only: `V<N>__<description>.sql`. Never edit an applied migration;
  add the next one.
- `spring.jpa.hibernate.ddl-auto=validate` in every service — schema comes from migrations only.
- Optimistic locking (`@Version`) where concurrent updates are plausible (inventory stock).

## Events

- Publish through the transactional outbox: the domain write and the `outbox_events` row commit in one
  transaction. Never write to Kafka inside a business transaction.
- Envelope shape is fixed: `eventId`, `eventType`, `eventVersion`, `occurredAt`, `producer`, `payload`.
- Consumers are idempotent on `eventId` via `processed_events`, keyed by `(event_id, consumer_name)`.
- The marker row and the effect commit in the same transaction, otherwise a crash drops work.
- Payloads carry no credentials, tokens, or password hashes. Personal data is limited to what the
  consumer demonstrably needs, and that choice is documented in the AsyncAPI contract.

## Errors

- Domain failures throw a service-local exception carrying a stable code and HTTP status
  (`IdentityException("EMAIL_ALREADY_EXISTS", ..., 409)`).
- Error codes are part of the public contract — renaming one is a breaking change.
- Responses use the shared `ApiError` envelope from `common-lib`. Every servlet service has a
  `@RestControllerAdvice`; the gateway does not, because it is WebFlux.
- Platform-wide codes every service emits: `VALIDATION_FAILED` (400), `INVALID_PARAMETER` (400),
  `MALFORMED_REQUEST` (400), `ACCESS_DENIED` (403), `DUPLICATE_RESOURCE` (409),
  `INTERNAL_ERROR` (500).
- A duplicate unique key is `DUPLICATE_RESOURCE`, not a 500. Pre-insert existence checks are not
  locks, so two concurrent requests can both pass one and the database rejects the loser.
  `ConstraintViolations` in `common-lib` classifies by SQLState (`23505` unique, `23503` foreign key,
  `23502` not null) rather than by exception subtype — Hibernate's JPA dialect collapses all three
  into one `DataIntegrityViolationException` and never narrows to `DuplicateKeyException`. Only a
  unique violation becomes 409; the other two stay 500, because they are service defects.
- Error bodies never carry a constraint name, table name, or SQLState. Those identify the schema and
  go to the log, where an operator can act on them.
- A 401 is not `ApiError`-shaped anywhere: it comes from the security filter chain's authentication
  entry point, which runs before the DispatcherServlet and so before any advice.
- **An `@ExceptionHandler(Exception.class)` catch-all must first re-dispatch `ErrorResponse`.**
  Declaring a handler for `Exception` takes precedence over Spring's `DefaultHandlerExceptionResolver`,
  so it silently captures the framework's own web exceptions — unknown path, unsupported method,
  unreadable body — which already carry a correct status, and reports each as 500. Spring 6 marks
  those with the `ErrorResponse` interface, so the catch-all forwards `errorResponse.getStatusCode()`
  before falling through to `INTERNAL_ERROR`.
- Two exceptions need their own handler because they do **not** implement `ErrorResponse` and would
  otherwise still land on the catch-all as 500. Both were found by probing, not by reading:
  `MethodArgumentTypeMismatchException` → 400 `INVALID_PARAMETER`, naming the parameter without
  repeating the rejected value; and `HttpMessageNotReadableException` → 400 `MALFORMED_REQUEST` for
  a body that is absent or is not valid JSON. Its message quotes the offending payload, so it is
  logged rather than returned.
- Confirmed as `ErrorResponse` implementors, and therefore handled by the re-dispatch:
  `NoResourceFoundException` (404), `HttpRequestMethodNotSupportedException` (405),
  `HttpMediaTypeNotSupportedException` (415), `HttpMediaTypeNotAcceptableException` (406). When
  adding a handler, probe the exception rather than assuming — the interface is not applied
  uniformly.
- The envelope is declared in the contract, not only in code. Every file under `contracts/openapi/`
  carries an identical `ApiError` schema and references it from each error response. Adding a status
  to a service means adding it to that service's contract in the same change — CI enforces the paths
  match and the schema copies are identical.

## API contract

- `contracts/openapi/*.v1.yaml` is canonical, one file per service, each standing alone.
- `tools/check-openapi-contract-drift.py` runs in CI as the `contracts` job and fails the build on:
  a declared endpoint no controller implements; an implemented endpoint no contract declares; a
  missing or divergent `ApiError` schema; a 4xx/5xx response with no schema; a status declared twice
  in one `responses:` block; a service with no contract or a contract with no service.
- **The code is the reference.** When the two disagree, the contract is what changes — unless the
  contract describes an intended feature, in which case it goes to the roadmap rather than being
  quietly implemented to satisfy the document.
- The checker refuses to run on a mapping annotation form it does not understand rather than
  skipping it. Extend the checker; do not work around it.
- What it does **not** check: path-variable *names* (`{id}` vs `{cropId}` both pass), request and
  response body schemas, or the `ApiError` copies against `ApiError.java`. Keep the copies in step
  with the Java record by hand — changing that record is a deliberate platform-wide event.

## Configuration

- Every externally settable value is an env var with a safe default:
  `${AGRICORE_REGISTRATION_ENABLED:true}`.
- Security-relevant defaults fail closed (`AGRICORE_DEV_MODE:false`,
  `AGRICORE_RATE_LIMIT_FAIL_OPEN:false`, `AGRICORE_TRUST_FORWARDED_HEADERS:false`).
- Optional infrastructure integrations are guarded by `@ConditionalOnProperty` so tests run without a
  broker: `agricore.outbox.publisher.enabled`, `agricore.kafka.consumer.enabled`.
- No secret values in `application.yml`, compose files, docs, or tests. Names only.

## Testing

- Unit tests drive real objects with mocked collaborators; they do not assert mock interactions when a
  real assertion is available.
- Contract/characterization tests lock event envelopes field by field — a rename must fail a test
  before it reaches a consumer.
- Integration tests use H2 with Flyway (`@ActiveProfiles("test")`), except where real PostgreSQL
  behavior is the point (`InventoryPostgresIdempotencyTest`, which **fails closed** without Docker).
- No `assumeTrue` / `@Disabled` to make a suite look green. A test that cannot run must fail.
- Test names read as behavior: `register_duplicateEmail_writesNoAdditionalEvent`.

## Commits

- Conventional Commits: `<type>(<scope>): <subject>`, imperative, ≤ 72 chars, no trailing period.
- Body explains **why**, wrapped at ~100 columns.
- One logical change per commit, each landing after its own validation gate.
- No AI/co-author trailers. `JasonTM17 <jasonbmt06@gmail.com>` is the only author.
- No plan ids, phase numbers, or audit labels in code comments, migration names, test names, or commit
  messages — describe the invariant instead.
