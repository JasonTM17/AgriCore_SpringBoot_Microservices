# AgriCore Security Review Notes

**Date:** 2026-07-22

**Scope:** Repository code, tests, configuration, and local Compose posture; production runtime deployment controls were not assessed

## Findings addressed

| ID | Severity | Finding | Mitigation |
|----|----------|---------|------------|
| S1 | High | Dual-write Kafka risk | Transactional outbox on harvest/farm/work |
| S2 | High | Duplicate harvest stock-in | `processed_events` unique key + test |
| S3 | Medium | Refresh token reuse | Family revoke on reuse (identity) |
| S4 | Medium | Weak password policy | Min length 8; BCrypt cost 12 |
| S5 | Medium | Public traceability leak | Public DTO omits internal IDs/PII |
| S6 | Low | Dev auth headers | `AGRICORE_DEV_MODE` only for local tests |
| S7 | High | Cross-farm ID substitution / IDOR | `farm_memberships` is the subject-to-farm authority; plot lookups join through the owning farm and mask inaccessible or mismatched plots as 404 |
| S8 | High | Downstream services trusted caller-supplied farm/plot IDs | Crop-cycle, work, harvest, and IoT verify request or stored resource IDs through farm-service before protected reads or mutations |
| S9 | Medium | Farm authorization dependency could fail open | `farm-access-client` maps network errors, unexpected statuses, invalid response bodies, and missing request authentication to `503 FARM_ACCESS_UNAVAILABLE` |
| S10 | Medium | Caller identity could be replaced on the internal hop | The client forwards the current caller's bearer token; farm-service validates it independently through the shared resource-server configuration |
| S11 | High | Assistant tool/provider egress could become an arbitrary data or network boundary | Farm tool is read-only, host-allowlisted, caller-token forwarding, response/row bounded, and fail-closed; provider configuration is environment/Secret-only and output is bounded plus deterministically screened |
| S12 | High | Durable generation replay could duplicate work or leak another user's conversation | Owner/farm scope checks, idempotency key and request hash, generation lease/versioning, ordered SSE replay, and redacted tool evidence are persisted in the assistant database |
| S13 | Medium | Assistant traffic could bypass per-user limits | Redis-backed request/token budgets key by authenticated user and source IP, return explicit 429 errors, and fail closed when Redis is unavailable |
| S14 | High | Permission catalog or role grants could be changed by a non-admin, duplicated, or partially replaced | Identity restricts all permission administration to `SYSTEM_ADMIN`; permission codes are database-unique; replacement locks the role and validates every requested code before changing grants |

## Open / deferred

| ID | Severity | Finding | Plan |
|----|----------|---------|------|
| O1 | Medium | Live gateway-to-service JWT enforcement was not exercised by this review | Add a compose/e2e negative-token check; code config already validates JWKS signature, issuer, and audience at gateway and domain services |
| O2 | Medium | Kafka ACLs not configured in compose | Define and verify production Kafka authentication/authorization before deployment; none is claimed here |
| O3 | Low | File upload not present | N/A until attachments ship |
| O4 | Medium | Provider egress TLS/Kafka ACLs remain deployment controls | Configure TLS/ACL policy in the target environment; local Compose intentionally uses an internal network and no provider by default |
| O5 | Medium | Role-grant changes do not invalidate permission snapshots in already-issued access tokens | Updated grants appear only in a newly issued token, such as after login or refresh; the old token remains valid until expiry. Default access-token TTL is 900 seconds. Evaluate immediate revocation if incident-response requirements demand it. |
| O6 | Medium | Permission authorities exist but no production endpoint enforces `PERMISSION_*` | Define and seed the catalog, add explicit `hasAuthority("PERMISSION_*")` policies with compatibility tests, then expose permission administration in the console. Until then, roles and farm membership remain the enforcement boundary. |

## Red-team checklist (sample)

- [x] Duplicate HarvestCompleted does not double stock
- [x] FIELD_WORKER cannot create farm (role check on farm-service)
- [x] Public QR response has no harvestBatchId
- [x] Farm creator receives initial membership; duplicate grants and removal of the last membership are rejected
- [x] Cross-farm plot substitution returns masked 404; `SYSTEM_ADMIN` is the explicit membership override
- [x] Farm-access denial, masked not-found, and unavailable responses prevent crop-cycle/work/harvest/IoT writes
- [x] Caller JWT forwarding, destination allowlisting, strict response decoding, and fail-closed client behavior have focused tests
- [ ] Live compose verification of invalid/expired JWT rejection at both gateway and a downstream service
- [x] Assistant output, citation, sensitive-data, refusal, idempotency, replay, tool-allowlist, and budget failure paths have focused tests
- [x] Permission creation and role-grant replacement require `SYSTEM_ADMIN`; duplicate codes and unknown-code atomic failure have focused tests
- [ ] Fine-grained `PERMISSION_*` endpoint enforcement (no production permission guard exists yet)

## Evidence

- Farm boundary and membership: `FarmAccessBoundaryIntegrationTest`, `FarmMembershipIntegrationTest`, `FarmMembershipConcurrencyIntegrationTest`.
- Client propagation and hardening: `DefaultFarmAccessClientTest`, `DefaultFarmAccessClientSecurityTest`, `FarmAccessPropertiesTest`.
- Downstream no-write and data-masking checks: `CropCycleAccessFailureIntegrationTest`, `CropCycleListAccessIntegrationTest`, `WorkAccessFailureIntegrationTest`, `WorkListAccessIntegrationTest`, `HarvestAccessFailureIntegrationTest`, `IotAccessFailureIntegrationTest`.
- JWT issuer/audience policy: `GatewaySecurityConfig`, `DomainServiceSecurityConfig`, and `AgricoreJwtValidatorsTest`. Gateway runtime security has only a context-load test in this repository, so no end-to-end claim is made.
- Permission persistence, administration, token claims, and authority conversion: `PermissionPersistenceIntegrationTest`, `AdminPermissionIntegrationTest`, `JwtTokenServiceTest`, `JwtAuthenticationFilterTest`, `JwtRolesConverterTest`, and the gateway `ApiGatewayApplicationTest` context load.

## Evidence boundary

All 13 Spring applications include the Micrometer OpenTelemetry bridge and OTLP exporter. Local Compose configures OTLP/HTTP trace export to Tempo, Prometheus scraping, Grafana provisioning, ECS JSON stdout, and Alloy/Loki collection with bounded local retention. This is repository and local Compose evidence only; the review does not claim that a production collector/backend is configured or that production trace delivery has been exercised.

This review does not prove runtime mTLS, Kafka ACL enforcement, production log tenancy/retention, or a deployed production environment. Alloy's read-only Docker socket remains a local host trust boundary.

The Docker/Helm edge is same-origin by default: console `/` and `/api` share the browser origin, assistant-service is internal-only in Compose, and the Helm Ingress applies request-size and long-lived SSE timeout controls. A Docker Hub push is a release action, not evidence of a production deployment; credentials must remain in repository secrets.

See [Microservices authorization model](./microservices-authz.md) for the current decision flow and status semantics.
