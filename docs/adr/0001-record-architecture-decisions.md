# 1. Record architecture decisions

**Date:** 2026-07-16

**Status:** Accepted

## Context

AgriCore needs a durable record of architectural choices for review, safe
evolution, and long-term maintenance.

## Decision

Store sequential Architecture Decision Records under `docs/adr/`. Records are
append-only and use `Proposed`, `Accepted`, `Deprecated`, or `Superseded`
status. A new decision supersedes an accepted record instead of silently
rewriting its history.

## Consequences

### Positive

- Decisions, alternatives, and ownership boundaries are searchable.
- Reviewers can distinguish intentional trade-offs from accidental structure.

### Negative

- Material architecture changes require a small documentation update.
- Stale ADRs are harmful unless code and decision links are reviewed together.

### Neutral

- The template defines the required headings but does not replace evidence.

## Trade-offs

The project accepts lightweight documentation overhead to reduce repeated
debates and unsafe reversals of verified decisions.

## Alternatives considered

- **Commit messages only:** rejected because decisions become scattered and hard
  to discover after squash or refactoring.
- **Wiki-only decisions:** rejected because a wiki revision cannot be reviewed
  atomically with code.
- **Mutable architecture overview only:** rejected because it describes current
  state but loses the alternatives and context behind it.

## References

- [ADR index](README.md)
- [ADR template](template.md)
- [System architecture](../architecture/SYSTEM_ARCHITECTURE.md)
