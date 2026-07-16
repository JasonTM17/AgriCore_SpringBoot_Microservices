# Local operations runbook

## Start infrastructure

```powershell
.\scripts\dev-up.ps1
# Postgres :5434  Redis :6380  Kafka :9092  Kafka UI :8088
```

## Build & test

```bash
mvn -B test
# Testcontainers inventory IT requires Docker
```

## Run core services (example)

```powershell
$env:AGRICORE_DEV_MODE="true"
$env:GATEWAY_JWT_ENABLED="false"  # optional for seed scripts without JWT
mvn -pl services/identity-service spring-boot:run
# farm, crop-catalog, crop-cycle, work, harvest, inventory, traceability, gateway ...
```

## Happy path

```powershell
.\scripts\e2e-happy-path.ps1
```

## Seed

```powershell
.\scripts\seed-data.ps1
```
