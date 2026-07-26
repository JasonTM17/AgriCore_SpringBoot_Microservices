# Design Guidelines

API and contract conventions. These are the rules a new endpoint or event must follow to look like it
belongs in this platform.

## REST shape

- Base path `/api/v1/<plural-resource>`; the public traceability read is the one exception,
  `/public/api/v1/traceability/{code}`, and the `public` prefix is what marks it unauthenticated.
- Resource names are plural and kebab-case: `/api/v1/crop-cycles`, `/api/v1/work-tasks`.
- State transitions are sub-resource POSTs, not PATCHes on a status field:
  `POST /api/v1/crop-cycles/{id}/stage`, `POST /api/v1/work-tasks/{id}/complete`,
  `POST /api/v1/inventory/reservations/{id}/confirm`. The verb is explicit and auditable.
- `PATCH` is for attribute edits only.
- Path ids are UUIDs. Human-facing lookups get an explicit route (`/api/v1/crops/by-code/{code}`).

## Status codes

| Situation | Code |
|-----------|------|
| Created a resource | 201 |
| Read or successful action | 200 |
| Action with no body | 204 |
| Validation failure | 400 |
| Missing/invalid token | 401 |
| Authenticated but not permitted | 403 |
| Unknown resource | 404 |
| Conflict with current state (duplicate email, stage rule) | 409 |
| Rate limited | 429 |

## Error envelope

Errors use `ApiError` from `common-lib` and always carry a stable machine-readable `code`
(`EMAIL_ALREADY_EXISTS`, `REGISTRATION_DISABLED`, `WAREHOUSE_NOT_FOUND`).

- Codes are `SCREAMING_SNAKE_CASE` and part of the public contract — renaming one is a breaking change.
- Messages are for humans and may change; clients branch on `code`, never on message text.
- Never leak internal detail: no stack traces, SQL, hostnames, or upstream URLs in a response body.

## Request and response bodies

- Java records for requests and responses, mapped explicitly — persistence entities are never
  serialized to clients.
- JSON fields are `camelCase`. Timestamps are ISO-8601 UTC instants (`2026-07-26T10:15:30Z`), never
  epoch numbers (`write-dates-as-timestamps: false`).
- Quantities that must not lose precision use decimal types, never floating point.
- Domain codes and status values stay in English (`FIELD_WORKER`, `HARVESTING`, `CONFIRMED`) even when
  the UI renders another language.
- Lists that can grow are paged with the shared `PageResponse` envelope.

## Event envelope

Fixed for every event, on every topic:

```json
{
  "eventId": "<uuid>",
  "eventType": "UserRegistered.v1",
  "eventVersion": 1,
  "occurredAt": "2026-07-26T10:15:30Z",
  "producer": "identity-service",
  "payload": { }
}
```

- `eventType` carries its version (`.v1`); `eventVersion` repeats it numerically.
- Schema: `contracts/event-schemas/DomainEventEnvelope.v1.json`. Topics and messages:
  `contracts/asyncapi/agricore-events.yaml`.
- Payload keys are `camelCase`, ids are UUID strings, and every payload field a consumer reads is
  locked by a producer-side contract test.
- Adding an optional field is compatible. Renaming or removing one requires a new `.v2` type, with both
  published until consumers move.
- Topics are `agricore.<domain>.events`; dead letters are `<topic>.DLT`.
- Payloads never carry credentials, tokens, or password hashes. Personal data is limited to what the
  consumer needs, and the reason is written into the AsyncAPI channel description.

## Contract ownership

- OpenAPI per service under `contracts/openapi/<service>.v1.yaml` is committed and kept truthful. When
  controller and contract disagree, controller behavior is the truth and the contract is fixed.
- A contract must not describe a consumer or producer that does not exist in code. If something is
  reserved but unimplemented, say so in the description — `NotificationRequested.v1` is the worked
  example.
- Breaking changes bump the version in the path (`/api/v2/...`); otherwise changes stay
  backward-compatible.

## Security defaults

- Everything requires a bearer token except `/public/**` and `/.well-known/jwks.json`.
- Domain services verify RS256 tokens locally against JWKS with issuer and audience checks — no
  per-request network hop to identity.
- Write endpoints are role-gated at the endpoint, not left to client discipline.
- New configuration flags default to the safe value; an operator opts into risk explicitly.

## UI-facing expectations

No frontend lives in this repository yet. When one arrives it must generate its client from the
committed OpenAPI contracts rather than hand-writing fetch wrappers, and it must render every state the
API can produce — loading, empty, forbidden, conflict, and the async gap where a read model has not yet
caught up with an event.
