# AgriCore happy-path smoke (services must run with AGRICORE_DEV_MODE=true where needed)
$ErrorActionPreference = "Stop"
$farm = "http://localhost:8082"
$cycle = "http://localhost:8084"
$work = "http://localhost:8085"
$harvest = "http://localhost:8087"
$inventory = "http://localhost:8086"
$trace = "http://localhost:8092"
$Headers = @{
  "Content-Type" = "application/json"
  "X-Dev-User" = "e2e"
  "X-Dev-Roles" = "FARM_MANAGER,AGRONOMIST,WAREHOUSE_MANAGER"
}

function PostJson([string]$Url, [hashtable]$Body) {
  return Invoke-RestMethod -Method Post -Uri $Url -Headers $Headers -Body ($Body | ConvertTo-Json -Depth 8)
}

Write-Host "1. Create farm + plot"
$farmObj = PostJson "$farm/api/v1/farms" @{ code="E2E-$(Get-Random)"; name="E2E Farm"; province="Dak Lak"; totalAreaHa=10 }
$plotObj = PostJson "$farm/api/v1/farms/$($farmObj.id)/plots" @{ code="P1"; name="Plot 1"; areaInHectares=1.0 }

Write-Host "2. Crop cycle"
$cycleObj = PostJson "$cycle/api/v1/crop-cycles" @{
  code="CC-$(Get-Random)"; farmId=$farmObj.id; plotId=$plotObj.id; cropId=[guid]::NewGuid()
  plannedStartDate="2026-01-01"; plannedEndDate="2026-12-01"
}
PostJson "$cycle/api/v1/crop-cycles/$($cycleObj.id)/stage" @{ stage="SOWING" } | Out-Null

Write-Host "3. Work task"
$taskObj = PostJson "$work/api/v1/work-tasks" @{
  code="WT-$(Get-Random)"; cropCycleId=$cycleObj.id; plotId=$plotObj.id
  taskType="IRRIGATION"; title="Water"; priority="HIGH"
}
PostJson "$work/api/v1/work-tasks/$($taskObj.id)/assign" @{ assignedEmployeeId=[guid]::NewGuid() } | Out-Null
PostJson "$work/api/v1/work-tasks/$($taskObj.id)/complete" @{ notes="ok" } | Out-Null

Write-Host "4. Warehouse + harvest + inventory"
$whObj = PostJson "$inventory/api/v1/inventory/warehouses" @{ code="WH-$(Get-Random)"; name="E2E WH" }
$harvestObj = PostJson "$harvest/api/v1/harvests/complete" @{
  code="HB-$(Get-Random)"; cropCycleId=$cycleObj.id; plotId=$plotObj.id; warehouseId=$whObj.id
  productCode="COFFEE-ROBUSTA"; grossWeightKg=100; netWeightKg=90; qualityGrade="GRADE_A"
}
$stockObj = PostJson "$inventory/api/v1/inventory/events/harvest-completed" @{
  eventId=$harvestObj.lastOutboxEventId; harvestBatchId=$harvestObj.id; warehouseId=$whObj.id
  productCode="COFFEE-ROBUSTA"; netWeightKg=90; qualityGrade="GRADE_A"
}
Write-Host "Stock on hand: $($stockObj.onHandQuantity)"

Write-Host "5. Traceability"
$trObj = PostJson "$trace/api/v1/traceability/batches" @{
  eventId=$harvestObj.lastOutboxEventId; harvestBatchId=$harvestObj.id
  cropCycleId=$cycleObj.id; plotId=$plotObj.id; farmName=$farmObj.name; plotCode=$plotObj.code
  productName="Ca phe Robusta"; harvestDate="2026-07-16"; qualityGrade="GRADE_A"
  netWeightKg=90; careSummary="Drip irrigation"
}
$publicObj = Invoke-RestMethod -Method Get -Uri "$trace/public/api/v1/traceability/$($trObj.traceabilityCode)"
Write-Host "Public product: $($publicObj.productName) code=$($publicObj.traceabilityCode)"
Write-Host "E2E happy path OK"
