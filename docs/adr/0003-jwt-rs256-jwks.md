# 3. JWT RS256 with JWKS

**Date:** 2026-07-16  
**Status:** Accepted

## Context

Symmetric HS256 is simpler but forces shared secrets across services and weakens key rotation story.

## Decision

Identity Service signs access tokens with **RS256** (RSA 2048+). Public keys published at `GET /.well-known/jwks.json`. Gateway and services verify via JWKS cache. Refresh tokens are **opaque** (random), stored hashed, rotated on use.

## Consequences

- Positive: private key stays in identity only; rotation-friendly
- Negative: slightly more setup than HS256
- Dev: generate ephemeral keypair at startup if no key path configured (never commit private keys)
