# 3. RS256 access tokens with JWKS

**Date:** 2026-07-16

**Status:** Accepted

## Context

Sharing an HMAC secret with every service expands the signing trust boundary and
weakens key rotation. Access tokens need short-lived identity, role, permission,
issuer, and audience claims, while refresh credentials need revocation and reuse
detection.

## Decision

- Identity signs access tokens with RSA-2048 or stronger using RS256.
- Public keys are published at `/.well-known/jwks.json`; the gateway and domain
  services validate signature, issuer, audience, and expiry.
- Access tokens are short-lived. Refresh tokens are opaque random values stored
  only as hashes, rotated on use, and family-revoked on reuse.
- Production key paths come from deployment secrets. An ephemeral key pair is
  allowed only for an explicit local-development mode.

## Consequences

### Positive

- The private signing key remains inside Identity.
- Verifiers can cache public keys and rotate by key ID.
- A database leak does not reveal plaintext refresh tokens.

### Negative

- Key generation, mounting, rotation, and JWKS cache behavior require operations
  procedures.
- Revoked role or permission changes do not alter an already-issued access-token
  snapshot before expiry.

### Neutral

- Domain services still perform authorization; a valid token alone is not access
  to every farm or operation.

## Trade-offs

The platform accepts asymmetric crypto setup and a bounded token-snapshot window
to avoid shared signing secrets and stateful validation on every API call.

## Alternatives considered

- **HS256:** rejected because every verifier would hold signing authority.
- **Opaque access tokens with introspection:** rejected because every request
  would depend on Identity availability or a second cache-consistency design.
- **Long-lived access tokens:** rejected because revocation latency becomes too
  large.
- **Store refresh tokens in plaintext:** rejected because a database compromise
  would immediately expose active credentials.

## References

- [Identity OpenAPI contract](../../contracts/openapi/identity-service.v1.yaml)
- [Domain JWT validation ADR](0007-domain-service-jwt-jwks.md)
- [Authorization model](../security/microservices-authz.md)
