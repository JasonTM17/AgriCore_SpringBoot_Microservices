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
        consoleService["Console Service"]
        gatewayService["Gateway Service + api-gateway alias"]
        workloads["Application workloads + internal Services\nread-only root + bounded /tmp"]
        policies["NetworkPolicies, configurable external egress,\noptional HPA/PDB (disabled by default), probes, security contexts"]
        assistantDbJob["Assistant database provisioning Job\npre-install/pre-upgrade when enabled"]
        iotPreflightJob["IoT TimescaleDB preflight Job\npre-install/pre-upgrade unless unsafe-skip"]
        ingress -->|"/"| consoleService
        ingress -->|"/api, /public/api"| gatewayService
        gatewayService --> workloads
        policies --- workloads
    end

    externalData["Operator-managed PostgreSQL, Redis, Kafka, MQTT, object storage, SMTP"]
    externalObs["Operator-managed OTLP and metrics/log backends"]
    registry["Docker Hub + GHCR immutable SHA images"]

    registry --> consoleService
    registry --> gatewayService
    registry --> workloads
    assistantDbJob -->|"creates assistant database"| externalData
    iotPreflightJob -->|"checks TimescaleDB extension"| externalData
    workloads --> externalData
    workloads -.->|"when configured"| externalObs
```

The enabled Helm Ingress sends `/` only to Console and `/api` plus `/public/api`
only to Gateway. Other chart-created service endpoints remain internal and are
not Ingress backends. The Assistant database-provisioning and IoT TimescaleDB
preflight Jobs are conditional pre-install/pre-upgrade hooks; they use
operator-provided Kubernetes Secrets for their database access.

The Helm chart deploys applications, not stateful platform dependencies. Secrets,
TLS, Kafka authorization, database backups, storage classes, and observability
retention remain production operator responsibilities. HPA and PDB resources are
available but disabled by default. Egress is open by default for external
dependencies; restricted deployments must supply explicit `additionalEgress`
rules. The dashed observability edge exists only when the corresponding external
endpoints are configured. The ingress policy also allows the matching
`ingress-nginx` controller namespace/pod labels while denying other non-AgriCore
ingress.
