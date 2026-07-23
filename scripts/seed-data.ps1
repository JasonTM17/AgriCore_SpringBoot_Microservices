# Seed sample AgriCore data via API Gateway + real JWT (microservices, no shared DB writes).
# Requires full stack (identity + farm + gateway). Dev-only passwords documented below.
param(
  [string]$Gateway = $(if ($env:GATEWAY_URL) { $env:GATEWAY_URL } else { "http://localhost:8080" }),
  [string]$Identity = $(if ($env:IDENTITY_URL) { $env:IDENTITY_URL } else { "http://localhost:8081" }),
  [ValidateRange(1, 8)]
  [int]$FarmLimit = 8,
  [ValidateRange(1, 12)]
  [int]$PlotsPerFarm = 6
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
  @{ code = "FARM-BT-01"; name = "Nong trai Binh Thuan"; province = "Binh Thuan"; totalAreaHa = 95.0; latitude = 10.93; longitude = 108.1 },
  @{ code = "FARM-LA-01"; name = "Nong trai Long An"; province = "Long An"; totalAreaHa = 64.0; latitude = 10.53; longitude = 106.41 },
  @{ code = "FARM-CT-01"; name = "Nong trai Can Tho"; province = "Can Tho"; totalAreaHa = 72.5; latitude = 10.045; longitude = 105.746 },
  @{ code = "FARM-AG-01"; name = "Nong trai An Giang"; province = "An Giang"; totalAreaHa = 110.0; latitude = 10.52; longitude = 105.125 },
  @{ code = "FARM-QN-01"; name = "Nong trai Quang Nam"; province = "Quang Nam"; totalAreaHa = 88.0; latitude = 15.54; longitude = 108.02 },
  @{ code = "FARM-SL-01"; name = "Nong trai Son La"; province = "Son La"; totalAreaHa = 76.0; latitude = 21.33; longitude = 103.91 }
)
$plotNames = @("Khu Bac", "Khu Nam", "Nha Luoi", "Vuon Uom", "Khu Dong", "Khu Tay", "Khu Trung", "Khu Phoi Tron", "Khu Dong Goi", "Khu Cach Ly", "Khu Thu Nghiem", "Khu Giong")
$soilTypes = @("BASALT", "ALLUVIAL", "LOAM", "SANDY_LOAM")
$farmsToSeed = $farms | Select-Object -First $FarmLimit
$seededFarmCount = 0
$seededPlotCount = 0
foreach ($f in $farmsToSeed) {
  try {
    $farm = PostJson "$Gateway/api/v1/farms" $Auth $f
    Write-Host "  farm $($farm.code) id=$($farm.id)"
    $seededFarmCount++
    for ($plotIndex = 0; $plotIndex -lt $PlotsPerFarm; $plotIndex++) {
      $plotCode = ($f.code -replace "FARM-", "") + "-P{0:00}" -f ($plotIndex + 1)
      $plot = PostJson "$Gateway/api/v1/farms/$($farm.id)/plots" $Auth @{
        code = $plotCode
        name = $plotNames[$plotIndex]
        areaInHectares = [math]::Round(2.5 + (($plotIndex % 4) * 0.75), 4)
        soilType = $soilTypes[$plotIndex % $soilTypes.Count]
      }
      $seededPlotCount++
      Write-Host "    plot $($plot.code) soil=$($soilTypes[$plotIndex % $soilTypes.Count])"
    }
  } catch {
    Write-Host "  skip farm $($f.code): $($_.Exception.Message)"
  }
}

Write-Host "Seed complete: $seededFarmCount farms, $seededPlotCount plots attempted."
Write-Host "Login manager@agricore.local / $DevPassword (DEV ONLY)."
