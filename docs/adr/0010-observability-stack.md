# 10. OpenTelemetry Observability Stack

**Date:** 2026-07-22
**Status:** Accepted

## Context

AgriCore has 13 independently deployed Spring applications. Operators need consistent request traces, service and domain metrics, dashboards, and machine-readable logs without coupling application code to one proprietary backend. Local development also needs a reproducible stack, while cluster deployments must remain usable when an observability backend is provided separately.

## Decision

1. Instrument all 13 Spring applications with Micrometer observations, `micrometer-tracing-bridge-otel`, and `opentelemetry-exporter-otlp`.
2. Export traces with OTLP/HTTP directly to Tempo. Local Compose uses `http://tempo:4318/v1/traces` and sampling probability `1.0`.
3. Expose `/actuator/prometheus` from each application. The local Prometheus configuration scrapes all 13 applications.
4. Provision Grafana with non-editable Prometheus and Tempo datasources, Prometheus exemplar links to Tempo, and seven file-backed AgriCore dashboards.
5. Emit Spring Boot ECS JSON to console stdout with service and environment metadata.
6. Keep cluster trace export opt-in. Helm leaves `observability.otlpTracingEndpoint` empty by default and uses sampling probability `0.1` when an endpoint is configured.
7. Provision Alloy and Loki only in local Compose. Alloy discovers containers for this Compose project through the read-only Docker socket, excludes Alloy/Loki self-logs, enriches service labels, and forwards ECS stdout to Loki. Loki uses persistent local filesystem storage with 72-hour retention; Docker json-file logs are independently bounded. Cluster deployments must provide their own collector, backend, persistence, retention, and access controls.

## Consequences

### Positive

- Application instrumentation is vendor-neutral and uses the Spring/Micrometer observation model.
- Metrics and traces are navigable together through Grafana datasources and exemplars.
- Local developers receive reproducible Tempo, Prometheus, Grafana, dashboards, full trace sampling, and searchable 72-hour logs.
- ECS JSON can be collected by Alloy locally or another deployment-specific collector without changing application log statements.

### Negative

- Trace export, histogram publication, and full local sampling add CPU, network, and storage overhead.
- Direct application-to-Tempo export provides no collector-side batching, routing, tail sampling, or retry policy.
- Local Tempo storage is container-local; its 48-hour retention setting is not a durability guarantee.
- Local Loki storage is persistent but intentionally bounded to 72 hours; the read-only Docker socket remains a host-level trust boundary.

### Neutral

- The Helm chart deploys application workloads, not Tempo, Prometheus, Loki, Alloy, Grafana, or a log collector.
- Operators choose the production OTLP endpoint, sampling policy, backend persistence, retention, and access controls.
- Grafana dashboards are provisioned read-only and are changed through repository JSON.

## Alternatives considered

- **OpenTelemetry Java agent:** rejected for the delivered stack because compile-time Micrometer integration matches Spring observations and keeps configuration visible in each application. The agent remains a deployment option if runtime-only instrumentation becomes necessary.
- **OpenTelemetry Collector between applications and Tempo:** deferred to keep local operation small. A collector is appropriate when production needs buffering, routing, enrichment, or tail sampling.
- **Jaeger- or Zipkin-specific tracing:** rejected to avoid backend-specific application integration; OTLP/HTTP preserves backend portability.
- **Metrics only:** rejected because cross-service gateway and domain workflows require trace-level correlation.
- **Promtail in the local stack:** rejected because Alloy is the current Grafana-supported collection path and provides Docker discovery, relabeling, and Loki forwarding with one bounded component.
- **Loki everywhere:** rejected because cluster storage, retention, auth, and tenancy are operator-specific; the repository provisions only a local development profile.

## References

- [Application Compose](../../docker-compose.yml)
- [Observability Compose](../../docker-compose.observability.yml)
- [Prometheus configuration](../../infrastructure/monitoring/prometheus.yml)
- [Tempo configuration](../../infrastructure/monitoring/tempo.yaml)
- [Loki configuration](../../infrastructure/monitoring/loki.yaml)
- [Alloy configuration](../../infrastructure/monitoring/alloy/config.alloy)
- [Grafana datasource provisioning](../../infrastructure/monitoring/grafana/provisioning/datasources/datasources.yml)
- [Grafana dashboard provisioning](../../infrastructure/monitoring/grafana/provisioning/dashboards/dashboards.yml)
- [Helm values](../../infrastructure/helm/agricore/values.yaml)
- [Helm application deployment template](../../infrastructure/helm/agricore/templates/deployment.yaml)
- [Local operations runbook](../runbooks/local-operations.md)
