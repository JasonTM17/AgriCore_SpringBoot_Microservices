# Deployment architecture

```mermaid
flowchart TB
    subgraph local["Local Docker Compose"]
        localEdge["Console + Nginx"]
        localApps["13 Spring applications"]
        localData["PostgreSQL, Redis, Kafka, MQTT, MinIO, Mailpit"]
        localObs["Prometheus, Tempo, Loki, Alloy, Grafana"]
        localEdge --> localApps --> localData
        localApps --> localObs
    end

    subgraph cluster["Kubernetes application chart"]
        ingress["Operator Ingress / TLS"]
        workloads["Application Deployments + Services\nread-only root + bounded /tmp"]
        gatewayAlias["api-gateway Service alias"]
        policies["NetworkPolicies, configurable external egress,\noptional HPA/PDB (disabled by default), probes, security contexts"]
        dbJob["Assistant database provisioning Job"]
        ingress --> workloads
        gatewayAlias --> workloads
        policies --- workloads
        dbJob --> workloads
    end

    externalData["Operator-managed PostgreSQL, Redis, Kafka, MQTT, object storage, SMTP"]
    externalObs["Operator-managed OTLP and metrics/log backends"]
    registry["Docker Hub + GHCR immutable SHA images"]

    registry --> workloads
    workloads --> externalData
    workloads --> externalObs
```

The Helm chart deploys applications, not stateful platform dependencies. Secrets,
TLS, Kafka authorization, database backups, storage classes, and observability
retention remain production operator responsibilities. HPA and PDB resources are
available but disabled by default. Egress is open by default for external
dependencies; restricted deployments must supply explicit `additionalEgress`
rules. The ingress policy also allows the matching `ingress-nginx` controller
namespace/pod labels while denying other non-AgriCore ingress.
