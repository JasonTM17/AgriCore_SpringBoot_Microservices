# Seed sample AgriCore data via API Gateway + real JWT (microservices, no shared DB writes).
# Requires full stack (identity + farm + gateway). Dev-only passwords documented below.
param(
  [string]$Gateway = $(if ($env:GATEWAY_URL) { $env:GATEWAY_URL } else { "http://localhost:8080" }),
  [string]$Identity = $(if ($env:IDENTITY_URL) { $env:IDENTITY_URL } else { "http://localhost:8081" })
)
$ErrorActionPreference = "Stop"

# DEV ONLY — never use in production
$DevPassword = "SeedPass123!"
$users = @(
  @{ email = "admin@agricore.local"; fullName = "System Admin"; roles = @("SYSTEM_ADMIN","FARM_MANAGER","WAREHOUSE_MANAGER") },
  @{ email = "manager@agricore.local"; fullName = "Farm Manager"; roles = @("FARM_MANAGER","AGRONOMIST") },
  @{ email = "agronomist@agricore.local"; fullName = "Agronomist"; roles = @("AGRONOMIST") },
  @{ email = "worker@agricore.local"; fullName = "Field Worker"; roles = @("FIELD_WORKER") },
  @{ email = "warehouse@agricore.local"; fullName = "Warehouse Manager"; roles = @("WAREHOUSE_MANAGER") }
)

function PostJson($Url, $Headers, $Body) {
  return Invoke-RestMethod -Method Post -Uri $Url -Headers $Headers -Body ($Body | ConvertTo-Json -Depth 8)
}

Write-Host "Seeding identity users (password for all: $DevPassword)..."
foreach ($u in $users) {
  try {
    PostJson "$Identity/api/v1/auth/register" @{ "Content-Type" = "application/json" } @{
      email = $u.email; password = $DevPassword; fullName = $u.fullName
    } | Out-Null
    Write-Host "  registered $($u.email)"
  } catch {
    Write-Host "  skip register $($u.email) (may exist)"
  }
  $roleList = ($u.roles | ForEach-Object { "'$_'" }) -join ","
  docker exec agricore-postgres psql -U agricore -d agricore_identity -c `
    "INSERT INTO user_roles (user_id, role_id) SELECT u.id, r.id FROM users u CROSS JOIN roles r WHERE u.email='$($u.email)' AND r.code IN ($roleList) ON CONFLICT DO NOTHING;" | Out-Null
}

$login = PostJson "$Gateway/api/v1/auth/login" @{ "Content-Type" = "application/json" } @{
  email = "manager@agricore.local"; password = $DevPassword
}
$token = $login.accessToken
if (-not $token) { $token = $login.access_token }
if (-not $token) { throw "Login failed for manager@agricore.local" }
$Auth = @{ "Content-Type" = "application/json"; "Authorization" = "Bearer $token" }

Write-Host "Seeding farms via gateway (JWT)..."
$farms = @(
  @{ code = "FARM-DL-01"; name = "Nong trai Dak Lak"; province = "Dak Lak"; totalAreaHa = 120.5; latitude = 12.6667; longitude = 108.05 },
  @{ code = "FARM-LD-01"; name = "Nong trai Lam Dong"; province = "Lam Dong"; totalAreaHa = 80.0; latitude = 11.94; longitude = 108.44 },
  @{ code = "FARM-BT-01"; name = "Nong trai Binh Thuan"; province = "Binh Thuan"; totalAreaHa = 95.0; latitude = 10.93; longitude = 108.1 }
)
foreach ($f in $farms) {
  try {
    $farm = PostJson "$Gateway/api/v1/farms" $Auth $f
    Write-Host "  farm $($farm.code) id=$($farm.id)"
    $plot = PostJson "$Gateway/api/v1/farms/$($farm.id)/plots" $Auth @{
      code = ($f.code -replace "FARM-", "") + "-A01"
      name = "Block A"
      areaInHectares = 2.5
      soilType = "BASALT"
    }
    Write-Host "  plot $($plot.code)"
  } catch {
    Write-Host "  skip farm $($f.code): $($_.Exception.Message)"
  }
}

Write-Host "Seed complete. Login manager@agricore.local / $DevPassword (DEV ONLY)."
