# AgriCore happy-path smoke (services must run with AGRICORE_DEV_MODE=true where needed)
$ErrorActionPreference = "Stop"
$farm = "http://localhost:8082"
$cycle = "http://localhost:8084"
$work = "http://localhost:8085"
$harvest = "http://localhost:8087"
$inventory = "http://localhost:8086"
$trace = "http://localhost:8092"
$H = @{ "Content-Type"="application/json"; "X-Dev-User"="e2e"; "X-Dev-Roles"="FARM_MANAGER,AGRONOMIST,WAREHOUSE_MANAGER" }

function Post($Url, $Body) {
  return Invoke-RestMethod -Method Post -Uri $Url -Headers $H -Body ($Body | ConvertTo-Json -Depth 8)
}

Write-Host "1. Create farm + plot"
$f = Post "$farm/api/v1/farms" @{ code="E2E-$(Get-Random)"; name="E2E Farm"; province="Dak Lak"; totalAreaHa=10 }
$p = Post "$farm/api/v1/farms/$($f.id)/plots" @{ code="P1"; name="Plot 1"; areaInHectares=1.0 }

Write-Host "2. Crop cycle"
$cc = Post "$cycle/api/v1/crop-cycles" @{
  code="CC-$(Get-Random)"; farmId=$f.id; plotId=$p.id; cropId=[guid]::NewGuid(); plannedStartDate="2026-01-01"; plannedEndDate="2026-12-01"
}
Post "$cycle/api/v1/crop-cycles/$($cc.id)/stage" @{ stage="SOWING" } | Out-Null

Write-Host "3. Work task"
$t = Post "$work/api/v1/work-tasks" @{
  code="WT-$(Get-Random)"; cropCycleId=$cc.id; plotId=$p.id; taskType="IRRIGATION"; title="Water"; priority="HIGH"
}
Post "$work/api/v1/work-tasks/$($t.id)/assign" @{ assignedEmployeeId=[guid]::NewGuid() } | Out-Null
Post "$work/api/v1/work-tasks/$($t.id)/complete" @{ notes="ok" } | Out-Null

Write-Host "4. Warehouse + harvest + inventory"
$wh = Post "$inventory/api/v1/inventory/warehouses" @{ code="WH-$(Get-Random)"; name="E2E WH" }
$h = Post "$harvest/api/v1/harvests/complete" @{
  code="HB-$(Get-Random)"; cropCycleId=$cc.id; plotId=$p.id; warehouseId=$wh.id;
  productCode="COFFEE-ROBUSTA"; grossWeightKg=100; netWeightKg=90; qualityGrade="GRADE_A"
}
$stock = Post "$inventory/api/v1/inventory/events/harvest-completed" @{
  eventId=$h.lastOutboxEventId; harvestBatchId=$h.id; warehouseId=$wh.id;
  productCode="COFFEE-ROBUSTA"; netWeightKg=90; qualityGrade="GRADE_A"
}
Write-Host "Stock on hand: $($stock.onHandQuantity)"

Write-Host "5. Traceability"
$tr = Post "$trace/api/v1/traceability/batches" @{
  eventId=$h.lastOutboxEventId; harvestBatchId=$h.id; cropCycleId=$cc.id; plotId=$p.id;
  farmName=$f.name; plotCode=$p.code; productName="Ca phe Robusta"; harvestDate="2026-07-16";
  qualityGrade="GRADE_A"; netWeightKg=90; careSummary="Drip irrigation"
}
$public = Invoke-RestMethod -Method Get -Uri "$trace/public/api/v1/traceability/$($tr.traceabilityCode)"
Write-Host "Public product: $($public.productName) code=$($public.traceabilityCode)"
Write-Host "E2E happy path OK"
