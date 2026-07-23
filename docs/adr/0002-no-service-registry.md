# 2. No service registry

**Date:** 2026-07-16

**Status:** Accepted

## Context

Docker Compose and Kubernetes already provide DNS-based service discovery.
Adding Eureka, Consul, or another application registry would duplicate that
capability.

## Decision

Do not run an application-level service registry. Compose service names and
Kubernetes Services are the stable discovery layer; gateway and downstream
client base URLs remain deployment configuration.

## Consequences

### Positive

- Fewer stateful control-plane components and failure modes.
- Local and cluster naming matches the deployment platform.

### Negative

- Bare-metal development needs explicit host configuration.
- Client-side discovery features must come from the platform or deployment
  configuration.

### Neutral

- Health probes and observability still monitor application availability; DNS
  discovery is not a health check.

## Trade-offs

AgriCore gives up registry-specific dashboards and client-side balancing to keep
the runtime aligned with Compose and Kubernetes primitives.

## Alternatives considered

- **Eureka:** rejected as duplicate discovery and another JVM service to operate.
- **Consul:** rejected because its broader service-mesh/value-store features are
  not required by the current deployment.
- **Nacos:** rejected for the same duplication and operational cost.
- **Hard-coded host addresses:** rejected because they are not portable.

## References

- [Application Compose](../../docker-compose.yml)
- [Helm workload and service template](../../infrastructure/helm/agricore/templates/deployment.yaml)
- [API gateway ADR](0014-api-gateway-and-same-origin-edge.md)
