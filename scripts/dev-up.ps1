# Start AgriCore stack.
# Default: infrastructure only (fast local jar workflows).
# Full platform gating: use scripts/verify-platform.ps1 (compose apps + health + e2e evidence).
param([switch]$Full)
$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot\..

if (-not (Test-Path .env)) {
  Copy-Item .env.example .env
  Write-Host "Created .env from .env.example"
}

if ($Full) {
  docker compose -f docker-compose.yml up -d --build
  Write-Host "Full stack building/starting. Gateway: http://localhost:8080"
  Write-Host "Gating verification: .\scripts\verify-platform.ps1 -EvidenceDir <path>"
} else {
  docker compose -f docker-compose.infrastructure.yml up -d
  Write-Host "Infrastructure starting. Kafka UI: http://localhost:8088"
  Write-Host "Apps: docker compose up --build   OR   .\scripts\verify-platform.ps1"
}
