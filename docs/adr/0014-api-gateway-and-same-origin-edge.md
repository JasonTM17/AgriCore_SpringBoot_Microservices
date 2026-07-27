# 14. API gateway and same-origin browser edge

**Date:** 2026-07-23

**Status:** Accepted

## Context

External clients need one stable entry point, while browser authentication must
avoid broad cross-origin policy and direct exposure of internal services. Domain
services must still enforce their own authorization when called inside the
network.

## Decision

Expose application APIs through Spring Cloud Gateway and serve the React console
behind an Nginx same-origin edge.

- Nginx serves static console assets and proxies `/api` and `/public/api` to the
  gateway.
- The gateway validates RS256 JWT issuer/audience/signature and forwards the
  caller bearer token.
- Public traceability routes are explicitly allowlisted; all other application
  routes require authentication.
- Domain services independently validate JWTs and enforce roles, permissions,
  and farm membership. The gateway is not the final authorization boundary.
- The assistant is reachable through the gateway but is not directly
  host-published in Compose.
- No service registry is required; Compose DNS and Kubernetes Services provide
  deployment-time discovery.

## Consequences

### Positive

- Clients use one origin and one route namespace.
- Internal ports and the assistant boundary are not browser-facing.
- Central token rejection and security headers complement service-level checks.

### Negative

- The gateway and Nginx edge are shared availability and capacity points.
- Route tables must stay aligned with OpenAPI and service ports.
- Token validation occurs at more than one hop.

### Trade-offs

The platform accepts duplicate JWT validation and an additional proxy hop to
preserve defense in depth and a simple browser security model.

## Alternatives considered

- **Browser calls every service directly:** rejected because it expands CORS,
  certificate, routing, and public-port management.
- **Gateway-only authorization:** rejected because an internal caller could
  bypass domain rules.
- **Backend-for-frontend with duplicated domain orchestration:** rejected because
  the current console needs routing and authentication, not another business
  owner.
- **Service registry:** rejected by [ADR 0002](0002-no-service-registry.md).

## References

- [Gateway OpenAPI contract](../../contracts/openapi/api-gateway.v1.yaml)
- [Console Nginx configuration](../../apps/agricore-console/nginx.conf)
- [JWT/JWKS ADR](0003-jwt-rs256-jwks.md)
- [Domain JWT validation ADR](0007-domain-service-jwt-jwks.md)
