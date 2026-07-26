function Sync-SeedProductionFlows {
  param([hashtable]$Context)

  $cropPage = Invoke-SeedJson GET `
    "$($Context.Gateway)/api/v1/crops?page=0&size=20" `
    $Context.AuthHeaders $null
  $crop = @($cropPage.content)[0]
  if (-not $crop) {
    throw "Seed requires at least one crop from the catalog migration."
  }

  $flowRecords = New-Object System.Collections.Generic.List[object]
  $domainPlots = @($Context.PrimaryPlots | Select-Object -First $Context.DomainFarmLimit)
  Write-Host "Seeding production flows for $($domainPlots.Count) farms..."

  for ($farmIndex = 0; $farmIndex -lt $domainPlots.Count; $farmIndex++) {
    $pair = $domainPlots[$farmIndex]
    $cycle = Sync-SeedCropCycle $Context $pair $crop
    Sync-SeedWorkTasks $Context $pair $cycle $farmIndex
    $warehouse = Sync-SeedWarehouse $Context $pair.Farm
    $harvest = Sync-SeedHarvest $Context $pair $cycle $warehouse $farmIndex

    $flowRecords.Add([pscustomobject]@{
        Farm = $pair.Farm
        Plot = $pair.Plot
        Cycle = $cycle
        Warehouse = $warehouse
        Harvest = $harvest
        InventoryItemId = $null
      }) | Out-Null
  }

  $flowRecordArray = $flowRecords.ToArray()
  Wait-SeedHarvestProjections $Context $flowRecordArray
  foreach ($record in $flowRecords) {
    $record.InventoryItemId = Invoke-SeedSqlScalar "agricore_inventory" `
      "SELECT id FROM inventory_items WHERE warehouse_id='$($record.Warehouse.id)' AND upper(sku)='COFFEE-ROBUSTA' ORDER BY created_at LIMIT 1;"
    if (-not $record.InventoryItemId) {
      throw "Inventory projection missing for warehouse $($record.Warehouse.code)."
    }
  }
  $Context.FlowRecords = $flowRecordArray
}

function Sync-SeedCropCycle {
  param(
    [hashtable]$Context,
    [object]$Pair,
    [object]$Crop
  )

  $cycleCode = "CYCLE-$($Pair.Farm.code)"
  $page = Invoke-SeedJson GET `
    "$($Context.Gateway)/api/v1/crop-cycles?plotId=$($Pair.Plot.id)&page=0&size=100" `
    $Context.AuthHeaders $null
  $cycle = @($page.content | Where-Object { $_.code -eq $cycleCode }) |
    Select-Object -First 1
  if (-not $cycle) {
    $cycle = Invoke-SeedJson POST "$($Context.Gateway)/api/v1/crop-cycles" `
      $Context.AuthHeaders @{
      code = $cycleCode
      farmId = $Pair.Farm.id
      plotId = $Pair.Plot.id
      cropId = $Crop.id
      plannedStartDate = "2026-01-15"
      plannedEndDate = "2026-12-15"
      notes = "Deterministic regional showcase season"
    }
    $Context.Counts.CyclesCreated++
    Add-SeedMutation $Context
  } else {
    $Context.Counts.CyclesReused++
  }

  $stages = @("PLANNED", "LAND_PREPARATION", "SOWING", "GROWING", "HARVESTING")
  $currentIndex = [array]::IndexOf($stages, [string]$cycle.stage)
  if ($currentIndex -lt 0) {
    throw "Unsupported seed crop-cycle stage '$($cycle.stage)'."
  }
  for ($index = $currentIndex + 1; $index -lt $stages.Count; $index++) {
    $cycle = Invoke-SeedJson POST `
      "$($Context.Gateway)/api/v1/crop-cycles/$($cycle.id)/stage" `
      $Context.AuthHeaders @{
      stage = $stages[$index]
      notes = "Seed lifecycle transition"
    }
    Add-SeedMutation $Context
  }
  return $cycle
}

function Sync-SeedWorkTasks {
  param(
    [hashtable]$Context,
    [object]$Pair,
    [object]$Cycle,
    [int]$FarmIndex
  )

  $page = Invoke-SeedJson GET `
    "$($Context.Gateway)/api/v1/work-tasks?plotId=$($Pair.Plot.id)&page=0&size=100" `
    $Context.AuthHeaders $null
  $taskByCode = @{}
  @($page.content) | ForEach-Object { $taskByCode[$_.code] = $_ }
  $taskTypes = @("IRRIGATION", "FERTILIZING", "PEST_CONTROL", "INSPECTION")
  $targetStatuses = @("COMPLETED", "IN_PROGRESS", "ASSIGNED", "CREATED")

  for ($taskIndex = 0; $taskIndex -lt $Context.TasksPerFarm; $taskIndex++) {
    $taskCode = "TASK-$($Pair.Farm.code)-{0:D2}" -f ($taskIndex + 1)
    $task = $taskByCode[$taskCode]
    if (-not $task) {
      $task = Invoke-SeedJson POST "$($Context.Gateway)/api/v1/work-tasks" `
        $Context.AuthHeaders @{
        code = $taskCode
        cropCycleId = $Cycle.id
        plotId = $Pair.Plot.id
        taskType = $taskTypes[$taskIndex % $taskTypes.Count]
        title = "Field operation $($taskIndex + 1) - $($Pair.Farm.name)"
        description = "Deterministic field evidence for the showcase dataset"
        priority = @("HIGH", "MEDIUM", "LOW")[$taskIndex % 3]
        scheduledStart = "2026-06-01T01:00:00Z"
        scheduledEnd = "2026-06-01T03:00:00Z"
      }
      $Context.Counts.TasksCreated++
      Add-SeedMutation $Context
    } else {
      $Context.Counts.TasksReused++
    }

    $targetStatus = $targetStatuses[$taskIndex % $targetStatuses.Count]
    if ($taskIndex -eq 0 -and
        @($task.attachments).Count -eq 0 -and
        $task.status -notin @("COMPLETED", "CANCELLED")) {
      $mediaPath = $Context.MediaFiles[$FarmIndex % $Context.MediaFiles.Count]
      Invoke-SeedAttachmentUpload `
        "$($Context.Gateway)/api/v1/work-tasks/$($task.id)/attachments" `
        $Context.Token $mediaPath | Out-Null
      $Context.Counts.AttachmentsUploaded++
      Add-SeedMutation $Context
    }

    if ($targetStatus -ne "CREATED" -and $task.status -eq "CREATED") {
      $task = Invoke-SeedJson POST `
        "$($Context.Gateway)/api/v1/work-tasks/$($task.id)/assign" `
        $Context.AuthHeaders @{ assignedEmployeeId = $Context.WorkerId }
      Add-SeedMutation $Context
    }
    if ($targetStatus -in @("IN_PROGRESS", "COMPLETED") -and
        $task.status -in @("ASSIGNED", "OVERDUE")) {
      $task = Invoke-SeedJson POST `
        "$($Context.Gateway)/api/v1/work-tasks/$($task.id)/start" `
        $Context.AuthHeaders $null
      Add-SeedMutation $Context
    }
    if ($targetStatus -eq "COMPLETED" -and $task.status -eq "IN_PROGRESS") {
      $task = Invoke-SeedJson POST `
        "$($Context.Gateway)/api/v1/work-tasks/$($task.id)/complete" `
        $Context.AuthHeaders @{ notes = "Completed by deterministic showcase seed" }
      Add-SeedMutation $Context
    }
  }
}

function Sync-SeedWarehouse {
  param(
    [hashtable]$Context,
    [object]$Farm
  )

  $warehouseCode = "WH-$($Farm.code)"
  $warehouseId = Invoke-SeedSqlScalar "agricore_inventory" `
    "SELECT id FROM warehouses WHERE upper(code)=upper('$warehouseCode') LIMIT 1;"
  if ($warehouseId) {
    $Context.Counts.WarehousesReused++
    return [pscustomobject]@{ id = $warehouseId; code = $warehouseCode }
  }

  $warehouse = Invoke-SeedJson POST `
    "$($Context.Gateway)/api/v1/inventory/warehouses" `
    $Context.AuthHeaders @{
    farmId = $Farm.id
    code = $warehouseCode
    name = "Kho so che $($Farm.name)"
  }
  $Context.Counts.WarehousesCreated++
  Add-SeedMutation $Context
  return $warehouse
}

function Sync-SeedHarvest {
  param(
    [hashtable]$Context,
    [object]$Pair,
    [object]$Cycle,
    [object]$Warehouse,
    [int]$FarmIndex
  )

  $harvestCode = "HARVEST-$($Pair.Farm.code)"
  $harvestId = Invoke-SeedSqlScalar "agricore_harvest" `
    "SELECT id FROM harvest_batches WHERE upper(code)=upper('$harvestCode') LIMIT 1;"
  if ($harvestId) {
    $Context.Counts.HarvestsReused++
    return [pscustomobject]@{ id = $harvestId; code = $harvestCode }
  }

  $netWeight = [decimal](120 + (($FarmIndex % 8) * 10))
  $harvest = Invoke-SeedJson POST "$($Context.Gateway)/api/v1/harvests/complete" `
    $Context.AuthHeaders @{
    code = $harvestCode
    cropCycleId = $Cycle.id
    plotId = $Pair.Plot.id
    warehouseId = $Warehouse.id
    productCode = "COFFEE-ROBUSTA"
    grossWeightKg = $netWeight + 12
    netWeightKg = $netWeight
    qualityGrade = @("GRADE_A", "GRADE_B")[$FarmIndex % 2]
    notes = "Regional showcase harvest"
    farmName = $Pair.Farm.name
    plotCode = $Pair.Plot.code
    productName = "Ca phe Robusta"
    careSummary = "Tuoi tiet kiem, theo doi IoT, nhat ky cong viec day du"
  }
  $Context.Counts.HarvestsCreated++
  Add-SeedMutation $Context
  return $harvest
}

function Wait-SeedHarvestProjections {
  param(
    [hashtable]$Context,
    [object[]]$FlowRecords
  )

  $warehouseIds = ConvertTo-SeedSqlUuidList @($FlowRecords | ForEach-Object {
      $_.Warehouse.id
    })
  $harvestIds = ConvertTo-SeedSqlUuidList @($FlowRecords | ForEach-Object {
      $_.Harvest.id
    })
  $expected = $FlowRecords.Count
  Wait-SeedCondition {
    $inventoryCount = [int](Invoke-SeedSqlScalar "agricore_inventory" `
        "SELECT count(DISTINCT warehouse_id) FROM inventory_items WHERE warehouse_id IN ($warehouseIds) AND upper(sku)='COFFEE-ROBUSTA';")
    $traceabilityCount = [int](Invoke-SeedSqlScalar "agricore_traceability" `
        "SELECT count(*) FROM traceability_batches WHERE harvest_batch_id IN ($harvestIds);")
    return $inventoryCount -eq $expected -and $traceabilityCount -eq $expected
  } "Harvest projections did not reach Inventory and Traceability within the timeout." 120
  Write-Host "  harvest projections ready inventory=$expected traceability=$expected"
}
