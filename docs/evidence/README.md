# Evidence register

This register separates prepared source-release requirements, published
source-release records, and historical snapshots. Repository evidence is useful
for source and package verification; it does not replace operator deployment
evidence.

## Prepared v1.0.1 provenance (not publication evidence)

| Record | What it establishes | What to verify |
|---|---|---|
| [v1.0.1 release manifest](../releases/v1.0.1.md) | Candidate scope, source-tag preflight, image-resolution procedure, and operator boundary | Annotated tag, matching successful default-branch CI and Docker Publish evidence |
| [Release provenance workflow](../../.github/workflows/release-provenance.yml) | Enforces the release version, annotated tag, exact default-branch target, and required successful runs before creating a GitHub Release | Exact target and recorded workflow evidence |
| [Deployment guide](../deployment-guide.md#image-verification) | Digest and signature verification procedure before an operator deploys | Resolved registry digest, signature, and environment-owned Helm values |

The prepared v1.0.1 manifest intentionally carries no source target, workflow
run, package tag, digest, or GitHub Release URL. It is not evidence that any of
those artifacts exists or that an operator deployment occurred.

## Published and historical source-release records

| Record | Scope | Qualification |
|---|---|---|
| [v1.0.0 release](https://github.com/JasonTM17/AgriCore_SpringBoot_Microservices/releases/tag/v1.0.0) | Prior published source release | Historical predecessor; not evidence for v1.0.1 |
| [v1.0.0 release manifest](../releases/v1.0.0.md) | Prior source-release scope and verification procedure | Historical predecessor; use the GitHub Release record for its published target/runs |

The release-provenance workflow requires an annotated `vMAJOR.MINOR.PATCH` tag
at the current default-branch tip. It records the exact successful CI and Docker
Publish runs before it creates a GitHub Release. Docker Publish promotes
short- and full-SHA image tags after candidate scanning, registry-parity, and
signature checks; deployment should use the resolved digest.

## Historical closeout and local snapshots

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
