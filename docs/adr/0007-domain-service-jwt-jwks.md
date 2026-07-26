# 7. Domain services validate JWT via identity JWKS

Date: 2026-07-16

## Status

Accepted

## Context

Domain services initially trusted gateway-only auth or decoded JWT payloads without signature verification (farm `DevJwtAuthenticationFilter`). That allows forged Bearer tokens when a service is reachable outside the gateway mesh.

## Decision

Introduce `libs/common-security` auto-configuration for servlet domain services:

- Production: Spring Security OAuth2 Resource Server with RS256 `JwtDecoder` from identity JWKS (`agricore.security.jwk-set-uri`) and issuer validation.
- Map string entries in JWT `roles` to `ROLE_*` and `permissions` to `PERMISSION_*`; ignore malformed or blank entries and deduplicate authorities.
- Dev/test only: `X-Dev-User` / `X-Dev-Roles` when `agricore.security.dev-mode=true`.
- Never accept unsigned JWT base64 payloads.
- Public paths remain limited to `/actuator/health/**`, `/actuator/prometheus`, `/public/**`.

Identity and API Gateway keep service-specific security configurations but apply the same role-and-permission mapping. Identity computes the sorted distinct permission union from the user's roles when issuing an access token.

## Consequences

### Positive

- Forged JWT without private key is rejected at each service boundary.
- Shared role-and-permission conversion reduces drift between the gateway and servlet domain services.
- Tests stay simple with explicit dev headers.

### Negative

- Domain services need network access to identity JWKS (or cached keys).
- Local unit tests without JWKS must set `dev-mode=true` and avoid real Bearer tokens.
- Role-grant changes do not alter existing token snapshots; updated permissions appear only in a newly issued token, such as after login or refresh. The old token remains valid with its prior claims until expiry (900 seconds by default).

### Neutral

- Gateway continues to validate JWT for external clients; service-level validation is defense in depth.
- Permission authorities are active at domain controller boundaries. The console also filters navigation using the effective permission snapshot; farm membership remains a separate resource-scope check.

## Alternatives considered

1. **Gateway-only trust with internal mTLS** — stronger mesh model, but not yet present in local compose; unsigned payload decode was not an acceptable interim.
2. **Symmetric HMAC shared secret** — simpler ops, weaker key management and no JWKS rotation story.

## References

- ADR-0003 JWT RS256 JWKS at identity
- `libs/common-security`
