# 2. No Service Registry (Eureka/Consul)

**Date:** 2026-07-16  
**Status:** Accepted

## Context

Spring Cloud Netflix Eureka is common in tutorials. Docker Compose and Kubernetes already provide DNS-based service discovery.

## Decision

Do **not** use Eureka or Consul in v1. Services call each other via stable DNS names (`identity-service:8081`, K8s Service names). Gateway routes use those names.

## Consequences

- Positive: less moving parts; aligns with production K8s
- Negative: local bare-metal multi-service needs hosts/file or compose
- Alternatives considered: Eureka, Consul, Nacos
