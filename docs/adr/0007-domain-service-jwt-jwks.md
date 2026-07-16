# 7. Domain services validate JWT via identity JWKS

Date: 2026-07-16

## Status

Accepted

## Context

Domain services initially trusted gateway-only auth or decoded JWT payloads without signature verification (farm `DevJwtAuthenticationFilter`). That allows forged Bearer tokens when a service is reachable outside the gateway mesh.

## Decision

Introduce `libs/common-security` auto-configuration for servlet domain services:

- Production: Spring Security OAuth2 Resource Server with RS256 `JwtDecoder` from identity JWKS (`agricore.security.jwk-set-uri`) and issuer validation.
- Dev/test only: `X-Dev-User` / `X-Dev-Roles` when `agricore.security.dev-mode=true`.
- Never accept unsigned JWT base64 payloads.
- Public paths remain limited to `/actuator/health/**`, `/actuator/prometheus`, `/public/**`.

Identity and API Gateway keep service-specific security configurations.

## Consequences

### Positive

- Forged JWT without private key is rejected at each service boundary.
- Single shared security primitive reduces drift across services.
- Tests stay simple with explicit dev headers.

### Negative

- Domain services need network access to identity JWKS (or cached keys).
- Local unit tests without JWKS must set `dev-mode=true` and avoid real Bearer tokens.

### Neutral

- Gateway continues to validate JWT for external clients; service-level validation is defense in depth.

## Alternatives considered

1. **Gateway-only trust with internal mTLS** — stronger mesh model, but not yet present in local compose; unsigned payload decode was not an acceptable interim.
2. **Symmetric HMAC shared secret** — simpler ops, weaker key management and no JWKS rotation story.

## References

- ADR-0003 JWT RS256 JWKS at identity
- `libs/common-security`
