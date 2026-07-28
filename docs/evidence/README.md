# Evidence register

This register separates current source-release provenance from historical
records. Repository evidence is useful for source and package verification; it
does not replace operator deployment evidence.

## Current source-release provenance

| Record | What it establishes | What to verify |
|---|---|---|
| [v1.0.0 release manifest](../releases/v1.0.0.md) | Source-release criteria, image-resolution procedure, and operator boundary | Annotated source tag, matching successful default-branch CI and Docker Publish evidence |
| [GitHub Releases](https://github.com/JasonTM17/AgriCore_SpringBoot_Microservices/releases) | The canonical release record when `release-provenance` creates it | Release target, linked CI and Docker Publish runs, and matching full-SHA image references |
| [Deployment guide](../deployment-guide.md#image-verification) | Digest and signature verification procedure before an operator deploys | Resolved registry digest, signature, and environment-owned Helm values |

The release-provenance workflow requires an annotated `vMAJOR.MINOR.PATCH` tag
at the current default-branch tip. It records the exact successful CI and Docker
Publish runs before it creates a GitHub Release. Docker Publish promotes
short- and full-SHA image tags after candidate scanning, registry-parity, and
signature checks; deployment should use the resolved digest.

## Historical closeout and snapshots

| Record | Scope | Qualification |
|---|---|---|
| [Release closeout — 2026-07-28](release-closeout-2026-07-28.md) | Verified predecessor revision and package publication | Historical; not evidence for a later source release or deployment |
| [Release verification — 2026-07-26](release-verification-2026-07-26.md) | Local release-candidate snapshot | Historical local evidence; not current-HEAD completion proof |

Read each historical record's revision, environment, and qualifications before
using it as evidence for a maintenance or release decision.

## Evidence limits

Source tags, CI runs, package publication, image tags, signatures, and digests
do **not** prove a production deployment. They also do not prove the
operator-owned controls that make a deployment safe: cluster access policy,
environment secrets, ingress/TLS, database backup and restore readiness, Kafka
authentication/ACLs/retention, external dependency availability, observability
retention, or rollout approval. Record those controls in the operator's own
deployment and incident evidence.

## Related references

- [Release provenance evidence chain](../diagrams/release-provenance.md)
- [Security policy](../../SECURITY.md)
- [Kafka retry/DLT runbook](../runbooks/kafka-dlq.md)
