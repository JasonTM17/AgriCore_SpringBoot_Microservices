# AgriCore Security Review Notes

**Date:** 2026-07-16  
**Scope:** Implemented services as of current monorepo

## Findings addressed

| ID | Severity | Finding | Mitigation |
|----|----------|---------|------------|
| S1 | High | Dual-write Kafka risk | Transactional outbox on harvest/farm/work |
| S2 | High | Duplicate harvest stock-in | `processed_events` unique key + test |
| S3 | Medium | Refresh token reuse | Family revoke on reuse (identity) |
| S4 | Medium | Weak password policy | Min length 8; BCrypt cost 12 |
| S5 | Medium | Public traceability leak | Public DTO omits internal IDs/PII |
| S6 | Low | Dev auth headers | `AGRICORE_DEV_MODE` only for local tests |

## Open / deferred

| ID | Severity | Finding | Plan |
|----|----------|---------|------|
| O1 | Medium | Service JWT not always JWKS-verified end-to-end | Gateway central verification next |
| O2 | Medium | Kafka ACLs not configured in compose | Production Kafka SASL/ACL |
| O3 | Low | File upload not present | N/A until attachments ship |

## Red-team checklist (sample)

- [x] Duplicate HarvestCompleted does not double stock
- [x] FIELD_WORKER cannot create farm (role check on farm-service)
- [x] Public QR response has no harvestBatchId
- [ ] Full JWT gateway enforcement in compose stack (in progress)
