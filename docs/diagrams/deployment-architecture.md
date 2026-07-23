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
        workloads["Application Deployments + Services"]
        policies["NetworkPolicies, HPA, PDB, probes, security contexts"]
        dbJob["Assistant database provisioning Job"]
        ingress --> workloads
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
retention remain production operator responsibilities.
