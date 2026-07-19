# Microservices authorization model

## Authentication boundaries

- The API Gateway validates RS256 access tokens against identity-service JWKS, issuer, and the `agricore-api` audience before proxying protected edge routes.
- Servlet domain services validate the forwarded bearer token again through `libs/common-security`; they do not trust gateway-only authentication.
- `X-Dev-User` and `X-Dev-Roles` are accepted only when `agricore.security.dev-mode=true`. Compose and Helm defaults set dev mode to false.
- Unsigned JWT payloads are never trusted.

## Farm ownership authority

`farm_memberships` is the authoritative mapping from the authenticated JWT subject to farms the subject may access. It grants farm scope only; operation roles still come from JWT authorities.

- Creating a farm grants its creator the initial membership inside the farm-creation transaction.
- Existing legacy farms were intentionally not auto-assigned by migration; `SYSTEM_ADMIN` can grant the first membership.
- Farm membership management requires `SYSTEM_ADMIN` or `FARM_MANAGER`. A non-admin manager must already belong to the farm.
- `(farm_id, subject)` is unique. Duplicate grants return `409 FARM_MEMBERSHIP_EXISTS`.
- The final membership cannot be revoked. Revocation locks the farm's membership rows so concurrent requests cannot orphan the farm.
- `ROLE_SYSTEM_ADMIN` is the only global membership override. Other roles do not imply access to every farm.

## Authorization flow

```text
caller bearer token
  -> API Gateway JWT validation
  -> domain-service JWT validation and role check
  -> crop-cycle/work/harvest/IoT access guard
  -> farm-access-client forwards the same bearer token
  -> farm-service resolves membership and authoritative plot ownership
  -> allow, masked not-found, denial, or fail-closed unavailable
```

The internal farm-access routes require authentication and are not gateway routes:

| Route | Verified decision |
|---|---|
| `GET /internal/api/v1/farm-access/farms/{farmId}` | Farm exists and the subject is a member, or the caller is `SYSTEM_ADMIN`. A non-member receives 403. |
| `GET /internal/api/v1/farm-access/plots/{plotId}` | Plot exists and belongs to an accessible farm. Missing and inaccessible plots both return 404. |
| `GET /internal/api/v1/farm-access/farms/{farmId}/plots/{plotId}` | Plot belongs to the supplied farm and that farm is accessible. Missing, inaccessible, or mismatched pairs return 404. |

The successful response contains authoritative `farmId` and nullable `plotId`. The client rejects responses whose IDs do not exactly match the request.

## Downstream resource checks

| Service | Guarded behavior |
|---|---|
| crop-cycle | Create verifies the request farm/plot pair. Get and stage change reload the cycle, then verify its stored farm/plot pair. Non-admin lists require an accessible `farmId` or `plotId`; supplying both verifies the pair. |
| work | Create verifies the request plot. Get, assign, and complete reload the task, then verify its stored plot. Non-admin lists require `plotId`; `cropCycleId`-only or global lists are `SYSTEM_ADMIN`-only. |
| harvest | Complete verifies the request plot before saving the batch or outbox event. Detail/status reload the batch, then verify its stored plot. Republish also requires a completion role and plot access before locking and requeueing the original event. |
| IoT | Device registration verifies the request plot. Reading ingestion reloads the device, then verifies the device's stored plot before updating last-seen state or writing readings/alerts. |

Role checks run before these resource checks. Passing a role check does not bypass farm membership.

## Failure and masking semantics

| Status | Meaning |
|---|---|
| 401 | Missing or invalid authentication at gateway or service boundary. |
| 403 | Authenticated caller lacks the required operation role, or a direct farm check denies membership. |
| 404 | Plot-linked resource is missing, outside the caller's farms, or mismatched. Downstream responses omit protected entity data. |
| 503 | Farm authorization cannot be trusted because the dependency failed, returned an unexpected status, sent an invalid/oversized response, or no forwardable request authentication exists. |

Downstream guards run before protected mutations. Access-failure integration tests assert no crop cycle, task, harvest batch, outbox event, IoT device, reading, or alert is written when authorization returns 403, 404, or 503.

## Caller-token propagation and client hardening

`libs/farm-access-client` forwards the current `JwtAuthenticationToken` as `Authorization: Bearer ...`. It uses dev headers only in explicit dev mode and fails closed when production request authentication is not a JWT.

The client also:

- restricts farm-service destinations to configured hosts;
- rejects credentials, paths, queries, and fragments in the base URL;
- requires HTTPS for non-loopback hosts unless insecure HTTP is explicitly enabled;
- bounds connect/read timeouts and response size;
- requires JSON and strictly rejects missing, unknown, duplicate, or trailing response fields.

This is caller-token authorization over configured HTTP(S). The repository does not demonstrate runtime mTLS.

## Public surface

- `/public/api/v1/traceability/{code}` is open and returns the documented public traceability projection.
- Identity `/api/v1/auth/**` and `/.well-known/jwks.json` are open for bootstrap.

## References

- [System architecture](../architecture/SYSTEM_ARCHITECTURE.md)
- [Security review](./SECURITY_REVIEW.md)
- Service contracts under `contracts/openapi/`, especially `farm-service.v1.yaml`, `crop-cycle-service.v1.yaml`, `work-service.v1.yaml`, `harvest-service.v1.yaml`, `iot-service.v1.yaml`, and `api-gateway.v1.yaml`.
