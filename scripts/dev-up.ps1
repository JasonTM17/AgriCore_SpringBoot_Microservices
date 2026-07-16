# Start AgriCore infrastructure (Postgres, Redis, Kafka)
$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot\..

if (-not (Test-Path .env)) {
  Copy-Item .env.example .env
  Write-Host "Created .env from .env.example"
}

docker compose -f docker-compose.infrastructure.yml up -d
Write-Host "Infrastructure starting. Kafka UI: http://localhost:8088"
Write-Host "Then run services with Maven or: docker compose up --build"
