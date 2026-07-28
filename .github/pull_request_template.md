## Summary

<!-- Explain the problem and the outcome. Link the related issue when one exists. -->

## Scope

- In scope:
- Out of scope:

## Trust boundaries and compatibility

<!-- Describe input validation, authorization, data exposure, and backward-compatibility considerations. -->

## Contract, data, and operational impact

<!-- List OpenAPI/AsyncAPI/schema changes, Flyway migrations, configuration, observability, deployment, rollback, or state "None". -->

## Verification

<!-- List the focused tests and shared gates actually run, with results. -->

## Reviewer checklist

- [ ] The change is focused and uses conventional commits.
- [ ] Public contracts are compatible or explicitly versioned.
- [ ] Generated API clients were regenerated when contracts changed.
- [ ] Database changes use a new Flyway migration; no applied migration was edited.
- [ ] Sensitive data, secrets, private keys, and local configuration are not included.
- [ ] Documentation and runbooks reflect user-visible, operational, or security changes.
- [ ] Required CI, security, contract, Compose, and Helm checks are green.
