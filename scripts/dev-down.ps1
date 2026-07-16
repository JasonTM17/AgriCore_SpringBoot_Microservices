$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot\..
docker compose down
docker compose -f docker-compose.infrastructure.yml down
Write-Host "AgriCore containers stopped."
