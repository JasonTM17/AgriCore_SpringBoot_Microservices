---
date: 2026-07-27 19:39 +07:00
session: release-registry-hardening
severity: high
component: docker-image-promotion
status: resolved
---

# Journal: 2026-07-27 — Release Registry Hardening

## Context

Release acceptance required the exact default-branch commit to pass CI and publish 14 digest-identical, signed images to Docker Hub and GHCR under full- and short-SHA tags. Publication evidence had to prove artifact state, not merely show that a workflow was configured correctly.

## What Happened

- Docker run `30252989247` promoted all 14 images, but identity verification failed twice with exit `255` even though the expected tags and signatures existed. The artifacts were good; the workflow could not reliably read the registry state it had just created.
- Hotfix PR `#47` added fail-closed retries with backoff and jitter plus regression tests. We chose targeted resilience instead of blindly rerunning a workflow whose failure mode was not yet fully understood.
- Docker run `30256538252` then built and signed all 14 candidates, but inventory promotion stopped when the first digest inspection exited `255`. Audit exposed the real defect: promotion used a one-shot registry read, while the candidate immutable-tag check treated any inspection error as “tag absent.” That was a dangerous fail-open lie.
- PR `#48` extracted shared registry retry logic, introduced an explicit absent-versus-unreadable tri-state, made digest-pinned tag creation idempotent and retryable, preserved candidate-tag output for failed-job reruns, and added three regression suites.
- Release-hardening SHA `472cc193e7e626617c4409172adf7c0295b626d7` passed CI run `30258476433`. Docker run `30259018941` completed `44/44` jobs, with direct parity proven for `14/14` images across both registries and both SHA tag forms.

## Reflection

This was exhausting because the release artifacts existed, yet our own verification repeatedly declared failure. Worse, one check could mistake an unreadable registry for an empty registry. We built a release gate that claimed to fail closed but had a fail-open branch at exactly the trust boundary that mattered. The second failed run was the kick in the teeth that proved PR `#47` fixed only part of the problem.

## Decisions

| Decision | Rationale | Impact |
|---|---|---|
| Never rerun blindly | A rerun can hide a broken state model and create more ambiguous evidence. | Operators must inspect artifact state before retrying. |
| Distinguish absent from unreadable | Registry exit `255` is not proof that a tag does not exist. | Read failures remain fail-closed. |
| Retry bounded transient failures | Registry reads and digest-pinned tag creation are idempotent but externally unreliable. | Backoff and jitter absorb transient faults without infinite loops. |
| Keep production deployment operator-owned | Package publication is not authorization to change a production environment. | Release acceptance does not imply deployment. |

## Next Steps

- Release maintainers: keep the three registry regression suites mandatory for every publish-workflow change.
- Release maintainers: preserve direct cross-registry `14/14` parity and signature verification as release evidence.
- Production operators: choose the deployment window and execute the separately governed production rollout.

## Unresolved Questions

- When will production operators schedule deployment, and which production environment will receive this accepted revision first?
