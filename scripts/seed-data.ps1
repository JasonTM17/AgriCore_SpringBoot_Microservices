# Seed sample AgriCore data against running local services (dev-mode headers).
$ErrorActionPreference = "Stop"
$gw = if ($env:GATEWAY_URL) { $env:GATEWAY_URL } else { "http://localhost:8082" }

function Invoke-Json($Method, $Url, $Body, $Roles = "FARM_MANAGER") {
  $headers = @{
    "Content-Type" = "application/json"
    "X-Dev-User" = "seed"
    "X-Dev-Roles" = $Roles
  }
  if ($Body) {
    return Invoke-RestMethod -Method $Method -Uri $Url -Headers $headers -Body ($Body | ConvertTo-Json -Depth 6)
  }
  return Invoke-RestMethod -Method $Method -Uri $Url -Headers $headers
}

Write-Host "Seeding farms..."
# Prefer direct farm-service when gateway JWT not wired for seed
$farmBase = "http://localhost:8082"
$farm = Invoke-Json POST "$farmBase/api/v1/farms" @{
  code = "FARM-DL-01"
  name = "Nong trai Dak Lak"
  address = "Buon Ma Thuot"
  province = "Dak Lak"
  totalAreaHa = 120.5
  latitude = 12.6667
  longitude = 108.05
}

Write-Host "Created farm $($farm.id)"
$plot = Invoke-Json POST "$farmBase/api/v1/farms/$($farm.id)/plots" @{
  code = "DL-A01"
  name = "Robusta Block A"
  areaInHectares = 2.5
  soilType = "BASALT"
}
Write-Host "Created plot $($plot.id)"
Write-Host "Seed complete (partial). Register identity users via /api/v1/auth/register."
