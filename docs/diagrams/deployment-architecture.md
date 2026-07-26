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
        policies["NetworkPolicies, configurable external egress,\nHPA, PDB, probes, security contexts"]
        dbJob["Assistant database provisioning Job"]
        ingress --> workloads
        gatewayAlias --> workloads
        policies --- workloads
        dbJob --> workloads
    end

    externalData["Operator-managed PostgreSQL, Redis, Kafka, MQTT, object storage, SMTP"]
    externalObs["Operator-managed OTLP and metrics/log backends"]
    registry["Docker Hub immutable SHA images"]

    registry --> workloads
    workloads --> externalData
    workloads --> externalObs
```

The Helm chart deploys applications, not stateful platform dependencies. Secrets,
TLS, Kafka authorization, database backups, storage classes, and observability
retention remain production operator responsibilities. Egress is open by default
for those external dependencies; restricted deployments must supply explicit
`additionalEgress` rules.
