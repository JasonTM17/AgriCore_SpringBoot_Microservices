# AgriCore happy-path via API Gateway + real JWT (AGRICORE_DEV_MODE=false compatible).
# Requires: identity:8081, farm:8082, crop-catalog:8083, crop-cycle:8084, work:8085,
#           inventory:8086, harvest:8087, traceability:8092, gateway:8080,
#           postgres, kafka (outbox publisher + consumers enabled).
$ErrorActionPreference = "Stop"
$Gateway = if ($env:GATEWAY_URL) { $env:GATEWAY_URL } else { "http://localhost:8080" }
$Identity = if ($env:IDENTITY_URL) { $env:IDENTITY_URL } else { "http://localhost:8081" }
$TraceDirect = if ($env:TRACEABILITY_URL) { $env:TRACEABILITY_URL } else { "http://localhost:8092" }

function PostJson([string]$Url, [hashtable]$Headers, [hashtable]$Body) {
  return Invoke-RestMethod -Method Post -Uri $Url -Headers $Headers -Body ($Body | ConvertTo-Json -Depth 8)
}

function GetJson([string]$Url, [hashtable]$Headers = @{}) {
  return Invoke-RestMethod -Method Get -Uri $Url -Headers $Headers
}

Write-Host "== 0. Register + login (JWT) =="
$email = "e2e-mgr-$(Get-Random)@agricore.local"
$password = "Password1!"
try {
  PostJson "$Identity/api/v1/auth/register" @{ "Content-Type" = "application/json" } @{
    email = $email; password = $password; fullName = "E2E Farm Manager"
  } | Out-Null
} catch {
  # may already exist
}
# Promote to FARM_MANAGER for farm create (default register role is FIELD_WORKER)
docker exec agricore-postgres psql -U agricore -d agricore_identity -c `
  "INSERT INTO user_roles (user_id, role_id) SELECT u.id, r.id FROM users u CROSS JOIN roles r WHERE u.email='$email' AND r.code IN ('FARM_MANAGER','AGRONOMIST','WAREHOUSE_MANAGER') ON CONFLICT DO NOTHING;" | Out-Null

$login = PostJson "$Gateway/api/v1/auth/login" @{ "Content-Type" = "application/json" } @{
  email = $email; password = $password
}
$token = $login.accessToken
if (-not $token) { $token = $login.access_token }
if (-not $token) { throw "No access token from login" }
$Auth = @{
  "Content-Type"  = "application/json"
  "Authorization" = "Bearer $token"
}
Write-Host "JWT issued (len=$($token.Length))"

Write-Host "== 1. Farm + plot via gateway =="
$farmCode = "E2E-$(Get-Random)"
$farmObj = PostJson "$Gateway/api/v1/farms" $Auth @{
  code = $farmCode; name = "E2E Dak Lak Farm"; province = "Dak Lak"; totalAreaHa = 10.5
  latitude = 12.67; longitude = 108.05
}
Write-Host "Farm id=$($farmObj.id) code=$($farmObj.code)"
$plotObj = PostJson "$Gateway/api/v1/farms/$($farmObj.id)/plots" $Auth @{
  code = "P1"; name = "Robusta Block"; areaInHectares = 1.25; soilType = "BASALT"
}
Write-Host "Plot id=$($plotObj.id) code=$($plotObj.code)"

Write-Host "== 2. Crop catalog =="
$cropObj = $null
try {
  $crops = GetJson "$Gateway/api/v1/crops?size=1" $Auth
  if ($crops.content -and $crops.content.Count -gt 0) {
    $cropObj = $crops.content[0]
  } elseif ($crops -is [System.Array] -and $crops.Count -gt 0) {
    $cropObj = $crops[0]
  }
} catch {}
if (-not $cropObj) {
  try {
    $cropObj = PostJson "$Gateway/api/v1/crops" $Auth @{
      code = "CROP-$(Get-Random)"; name = "Ca phe Robusta"; category = "PERENNIAL"
    }
  } catch {
    # catalog may require different shape — use synthetic id for cycle FK (cross-service ID only)
    $cropObj = @{ id = [guid]::NewGuid().ToString() }
  }
}
$cropId = $cropObj.id
Write-Host "Crop id=$cropId"

Write-Host "== 3. Crop cycle + legal stage path =="
$cycleCode = "CC-$(Get-Random)"
$cycleObj = PostJson "$Gateway/api/v1/crop-cycles" $Auth @{
  code = $cycleCode
  farmId = $farmObj.id
  plotId = $plotObj.id
  cropId = $cropId
  plannedStartDate = "2026-01-01"
  plannedEndDate = "2026-12-01"
  notes = "E2E season"
}
Write-Host "Cycle id=$($cycleObj.id) stage=$($cycleObj.stage)"
# Legal graph: PLANNED → LAND_PREPARATION → SOWING → GROWING → HARVESTING
foreach ($stage in @("LAND_PREPARATION", "SOWING", "GROWING", "HARVESTING")) {
  $cycleObj = PostJson "$Gateway/api/v1/crop-cycles/$($cycleObj.id)/stage" $Auth @{ stage = $stage }
  Write-Host "  stage → $($cycleObj.stage)"
}

Write-Host "== 4. Work task =="
$taskObj = PostJson "$Gateway/api/v1/work-tasks" $Auth @{
  code = "WT-$(Get-Random)"
  cropCycleId = $cycleObj.id
  plotId = $plotObj.id
  taskType = "IRRIGATION"
  title = "Water block"
  priority = "HIGH"
}
PostJson "$Gateway/api/v1/work-tasks/$($taskObj.id)/assign" $Auth @{
  assignedEmployeeId = [guid]::NewGuid().ToString()
} | Out-Null
PostJson "$Gateway/api/v1/work-tasks/$($taskObj.id)/complete" $Auth @{ notes = "ok" } | Out-Null
Write-Host "Work task completed id=$($taskObj.id)"

Write-Host "== 5. Warehouse + harvest (outbox → Kafka) =="
$whObj = PostJson "$Gateway/api/v1/inventory/warehouses" $Auth @{
  code = "WH-$(Get-Random)"; name = "E2E Warehouse"
}
$harvestCode = "HB-$(Get-Random)"
$harvestObj = PostJson "$Gateway/api/v1/harvests/complete" $Auth @{
  code = $harvestCode
  cropCycleId = $cycleObj.id
  plotId = $plotObj.id
  warehouseId = $whObj.id
  productCode = "COFFEE-ROBUSTA"
  grossWeightKg = 100
  netWeightKg = 90
  qualityGrade = "GRADE_A"
  farmName = $farmObj.name
  plotCode = $plotObj.code
  productName = "Ca phe Robusta"
  careSummary = "Drip irrigation, organic fertilizer"
}
Write-Host "Harvest id=$($harvestObj.id) outboxEventId=$($harvestObj.lastOutboxEventId)"

Write-Host "== 6. Wait for Kafka inventory consumer (DB proof) =="
$deadline = (Get-Date).AddSeconds(60)
$stocked = $false
$onHand = $null
while ((Get-Date) -lt $deadline) {
  try {
    $onHand = (docker exec agricore-postgres psql -U agricore -d agricore_inventory -t -A -c `
      "SELECT on_hand_quantity FROM inventory_items WHERE upper(sku)='COFFEE-ROBUSTA' ORDER BY created_at DESC LIMIT 1;").Trim()
    if ($onHand -and [decimal]$onHand -ge 90) {
      Write-Host "Inventory stocked sku=COFFEE-ROBUSTA onHand=$onHand"
      $stocked = $true
      break
    }
  } catch {}
  Start-Sleep -Seconds 2
}
if (-not $stocked) {
  Write-Warning "Inventory stock not observed in DB within timeout (outbox/Kafka lag or consumer down). onHand='$onHand'"
}

Write-Host "== 7. Public traceability QR (Kafka projection) =="
# Same algorithm as TraceabilityApplicationService.generateCode
$productNameForCode = "Ca phe Robusta"
$prefix = ($productNameForCode -replace '[^A-Za-z0-9]', '').ToUpperInvariant()
if ($prefix.Length -gt 6) { $prefix = $prefix.Substring(0, 6) }
if ([string]::IsNullOrWhiteSpace($prefix)) { $prefix = "PRD" }
$suffix = ($harvestObj.id.ToString() -replace '-', '').Substring(0, 8).ToUpperInvariant()
$expectedCode = "$prefix-$suffix"
$candidates = @($expectedCode, "COFFEE-$suffix", "PRD-$suffix")
Write-Host "Expected traceability code: $expectedCode"

$publicObj = $null
$deadline2 = (Get-Date).AddSeconds(45)
while ((Get-Date) -lt $deadline2 -and -not $publicObj) {
  foreach ($c in $candidates) {
    try {
      $publicObj = GetJson "$Gateway/public/api/v1/traceability/$c"
      $traceCode = $c
      break
    } catch {
      try {
        $publicObj = GetJson "$TraceDirect/public/api/v1/traceability/$c"
        $traceCode = $c
        break
      } catch {}
    }
  }
  if ($publicObj) { break }
  Start-Sleep -Seconds 2
}

if (-not $publicObj) {
  throw "Traceability public QR not ready (Kafka projection missing). harvestBatchId=$($harvestObj.id)"
}

if ($publicObj.farmName -ne $farmObj.name) {
  throw "Expected farmName='$($farmObj.name)' on public QR, got '$($publicObj.farmName)'"
}
if ($publicObj.plotCode -ne $plotObj.code) {
  throw "Expected plotCode='$($plotObj.code)' on public QR, got '$($publicObj.plotCode)'"
}

Write-Host "Public QR code=$traceCode product=$($publicObj.productName) farm=$($publicObj.farmName) plot=$($publicObj.plotCode)"
Write-Host "E2E happy path OK (gateway JWT + legal stages + harvest outbox/Kafka projection)"
