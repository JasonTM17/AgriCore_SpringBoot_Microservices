function Sync-SeedConnectedExperiences {
  param([hashtable]$Context)

  Sync-SeedIotReadings $Context
  Sync-SeedSalesOrders $Context
  Sync-SeedAssistantConversation $Context
  Write-SeedAuthoritativeSummary $Context
}

function Sync-SeedIotReadings {
  param([hashtable]$Context)

  Write-Host "Seeding IoT devices and readings..."
  $deviceIds = New-Object System.Collections.Generic.List[string]
  for ($farmIndex = 0; $farmIndex -lt $Context.FlowRecords.Count; $farmIndex++) {
    $record = $Context.FlowRecords[$farmIndex]
    $deviceCode = "SEED-$($record.Farm.code)"
    $deviceId = Invoke-SeedSqlScalar "agricore_iot" `
      "SELECT id FROM devices WHERE upper(device_code)=upper('$deviceCode') LIMIT 1;"
    if (-not $deviceId) {
      $device = Invoke-SeedJson POST "$($Context.Gateway)/api/v1/iot/devices" `
        $Context.AuthHeaders @{
        deviceCode = $deviceCode
        plotId = $record.Plot.id
        name = "Tram cam bien $($record.Plot.code)"
      }
      $deviceId = $device.id
      $Context.Counts.DevicesCreated++
      Add-SeedMutation $Context
    } else {
      $Context.Counts.DevicesReused++
    }
    $deviceIds.Add([string]$deviceId) | Out-Null

    for ($readingIndex = 0; $readingIndex -lt $Context.ReadingsPerDevice; $readingIndex++) {
      $readingId = New-SeedDeterministicGuid "$deviceCode-reading-$readingIndex"
      $recordedAt = [DateTimeOffset]::Parse("2026-06-01T00:00:00Z").
        AddMinutes(($farmIndex * 1000) + $readingIndex).
        ToString("o")
      $value = if (($readingIndex % 10) -eq 9) {
        [decimal]18.5
      } else {
        [decimal](42 + (($farmIndex + $readingIndex) % 24))
      }
      Invoke-SeedJson POST "$($Context.Gateway)/api/v1/iot/readings" `
        $Context.AuthHeaders @{
        readingId = $readingId
        deviceCode = $deviceCode
        metricType = "SOIL_MOISTURE"
        metricValue = $value
        unit = "%"
        recordedAt = $recordedAt
      } | Out-Null
      $Context.Counts.ReadingsSynchronized++
      Add-SeedMutation $Context
    }
  }

  $deviceIdList = ConvertTo-SeedSqlUuidList $deviceIds.ToArray()
  $expectedReadings = $Context.FlowRecords.Count * $Context.ReadingsPerDevice
  $actualReadings = [int](Invoke-SeedSqlScalar "agricore_iot" `
      "SELECT count(*) FROM sensor_reading_idempotency WHERE device_id IN ($deviceIdList);")
  if ($actualReadings -lt $expectedReadings) {
    throw "IoT seed verification expected at least $expectedReadings readings, found $actualReadings."
  }
}

function Sync-SeedSalesOrders {
  param([hashtable]$Context)

  Write-Host "Seeding sales saga orders..."
  $confirmedOrders = New-Object System.Collections.Generic.List[object]
  $orderRecords = @($Context.FlowRecords | Select-Object -First $Context.SalesOrderLimit)
  for ($orderIndex = 0; $orderIndex -lt $orderRecords.Count; $orderIndex++) {
    $record = $orderRecords[$orderIndex]
    $customerCode = "SEED-CUST-$($record.Farm.code)"
    $customerId = Invoke-SeedSqlScalar "agricore_sales" `
      "SELECT id FROM customers WHERE upper(code)=upper('$customerCode') AND farm_id='$($record.Farm.id)' LIMIT 1;"
    if (-not $customerId) {
      $customer = Invoke-SeedJson POST `
        "$($Context.Gateway)/api/v1/sales/customers" `
        $Context.AuthHeaders @{
        farmId = $record.Farm.id
        code = $customerCode
        name = "Khach hang $($record.Farm.name)"
        email = "orders@agricore.local"
      }
      $customerId = $customer.id
      $Context.Counts.CustomersCreated++
      Add-SeedMutation $Context
    } else {
      $Context.Counts.CustomersReused++
    }

    $orderNumber = "SO-$($record.Farm.code)-{0:D2}" -f ($orderIndex + 1)
    $upgradedOrderId = Invoke-SeedSqlScalar "agricore_sales" `
      "UPDATE sales_orders SET farm_id='$($record.Farm.id)', customer_id='$customerId' WHERE upper(order_number)=upper('$orderNumber') AND farm_id IS NULL RETURNING id;"
    if ($upgradedOrderId) {
      Add-SeedMutation $Context
    }
    $existing = Invoke-SeedSqlScalar "agricore_sales" `
      "SELECT id || '|' || correlation_id || '|' || status FROM sales_orders WHERE upper(order_number)=upper('$orderNumber') AND farm_id='$($record.Farm.id)' LIMIT 1;"
    if ($existing) {
      $parts = $existing -split '\|'
      $order = [pscustomobject]@{
        id = $parts[0]
        correlationId = $parts[1]
        status = $parts[2]
      }
      $Context.Counts.OrdersReused++
    } else {
      $order = Invoke-SeedJson POST "$($Context.Gateway)/api/v1/sales/orders" `
        $Context.AuthHeaders @{
        orderNumber = $orderNumber
        farmId = $record.Farm.id
        customerId = $customerId
        inventoryItemId = $record.InventoryItemId
        quantity = [decimal](5 + ($orderIndex % 4))
        unitPrice = [decimal](72000 + (($orderIndex % 5) * 1500))
        currencyCode = "VND"
      }
      $Context.Counts.OrdersCreated++
      Add-SeedMutation $Context
    }

    if ($order.status -ne "CONFIRMED") {
      Wait-SeedCondition {
        $script:seedOrder = Invoke-SeedJson GET `
          "$($Context.Gateway)/api/v1/sales/orders/$($order.id)" `
          $Context.AuthHeaders $null
        return $script:seedOrder.status -eq "CONFIRMED"
      } "Sales order $orderNumber did not reach CONFIRMED." 60
      $order = $script:seedOrder
    }
    $confirmedOrders.Add($order) | Out-Null
  }

  if ($confirmedOrders.Count -gt 0) {
    $correlationIds = ConvertTo-SeedSqlUuidList @($confirmedOrders | ForEach-Object {
        $_.correlationId
      })
    $Context.SeedSalesCorrelationIds = @($confirmedOrders | ForEach-Object {
        $_.correlationId
      })
    Wait-SeedCondition {
      $count = [int](Invoke-SeedSqlScalar "agricore_notification" `
          "SELECT count(*) FROM notifications WHERE correlation_id IN ($correlationIds) AND source_event_type='SalesOrderConfirmed.v1';")
      return $count -ge $confirmedOrders.Count
    } "Sales confirmation notifications did not reach Notification service." 90
  }
}

function Sync-SeedAssistantConversation {
  param([hashtable]$Context)

  Write-Host "Seeding bounded assistant conversation..."
  $title = "Tong quan nong trai mau AgriCore"
  $page = Invoke-SeedJson GET `
    "$($Context.Gateway)/api/v1/assistant/conversations?status=OPEN&page=0&size=100" `
    $Context.AuthHeaders $null
  $conversation = @($page.content | Where-Object { $_.title -eq $title }) |
    Select-Object -First 1
  if (-not $conversation) {
    $farm = $Context.FlowRecords[0].Farm
    $conversation = Invoke-SeedJson POST `
      "$($Context.Gateway)/api/v1/assistant/conversations" `
      $Context.AuthHeaders @{
      title = $title
      contextType = "FARM"
      farmId = $farm.id
    }
    $Context.Counts.ConversationsCreated++
    Add-SeedMutation $Context
  } else {
    $Context.Counts.ConversationsReused++
  }

  $capabilities = Invoke-SeedJson GET `
    "$($Context.Gateway)/api/v1/assistant/capabilities" `
    $Context.AuthHeaders $null
  if (-not $capabilities.available) {
    Write-Host "  assistant provider=$($capabilities.provider) unavailable reason=$($capabilities.reasonCode); conversation retained, generation skipped"
    return
  }

  $generationHeaders = @{}
  foreach ($key in $Context.AuthHeaders.Keys) {
    $generationHeaders[$key] = $Context.AuthHeaders[$key]
  }
  $generationHeaders["Idempotency-Key"] = "seed-farm-overview-v1"
  $generation = Invoke-SeedJson POST `
    "$($Context.Gateway)/api/v1/assistant/conversations/$($conversation.id)/generations" `
    $generationHeaders @{
    prompt = "Tom tat tinh trang nong trai va neu cac buoc can kiem tra truoc vu thu hoach."
  }
  Add-SeedMutation $Context

  if ($generation.status -notin @("COMPLETED", "FAILED", "CANCELLED")) {
    Wait-SeedCondition {
      $script:seedGeneration = Invoke-SeedJson GET `
        "$($Context.Gateway)/api/v1/assistant/conversations/$($conversation.id)/generations/$($generation.id)" `
        $Context.AuthHeaders $null
      return $script:seedGeneration.status -in @("COMPLETED", "FAILED", "CANCELLED")
    } "Assistant seed generation did not reach a terminal state." 45
    $generation = $script:seedGeneration
  }
  Write-Host "  assistant generation status=$($generation.status)"
}

function Write-SeedAuthoritativeSummary {
  param([hashtable]$Context)

  $salesNotificationCount = 0
  if (@($Context.SeedSalesCorrelationIds).Count -gt 0) {
    $correlationIds = ConvertTo-SeedSqlUuidList @($Context.SeedSalesCorrelationIds)
    $salesNotificationCount = [int](Invoke-SeedSqlScalar "agricore_notification" `
        "SELECT count(*) FROM notifications WHERE correlation_id IN ($correlationIds) AND source_event_type='SalesOrderConfirmed.v1';")
  }
  $summary = [ordered]@{
    Farms = [int](Invoke-SeedSqlScalar "agricore_farm" `
        "SELECT count(*) FROM farms WHERE code LIKE 'FARM-%';")
    Plots = [int](Invoke-SeedSqlScalar "agricore_farm" `
        "SELECT count(*) FROM plots WHERE code ~ '^[A-Z]{2}-[0-9]{2}-P[0-9]{2}$';")
    CropCycles = [int](Invoke-SeedSqlScalar "agricore_crop_cycle" `
        "SELECT count(*) FROM crop_cycles WHERE code LIKE 'CYCLE-FARM-%';")
    WorkTasks = [int](Invoke-SeedSqlScalar "agricore_work" `
        "SELECT count(*) FROM work_tasks WHERE code LIKE 'TASK-FARM-%';")
    Harvests = [int](Invoke-SeedSqlScalar "agricore_harvest" `
        "SELECT count(*) FROM harvest_batches WHERE code LIKE 'HARVEST-FARM-%';")
    TraceabilityBatches = [int](Invoke-SeedSqlScalar "agricore_traceability" `
        "SELECT count(*) FROM traceability_batches WHERE farm_name LIKE 'Nong trai %';")
    Devices = [int](Invoke-SeedSqlScalar "agricore_iot" `
        "SELECT count(*) FROM devices WHERE device_code LIKE 'SEED-FARM-%';")
    Readings = [int](Invoke-SeedSqlScalar "agricore_iot" `
        "SELECT count(*) FROM sensor_reading_idempotency i JOIN devices d ON d.id=i.device_id WHERE d.device_code LIKE 'SEED-FARM-%';")
    SalesOrders = [int](Invoke-SeedSqlScalar "agricore_sales" `
        "SELECT count(*) FROM sales_orders WHERE order_number LIKE 'SO-FARM-%';")
    SalesNotifications = $salesNotificationCount
    AssistantConversations = [int](Invoke-SeedSqlScalar "agricore_assistant" `
        "SELECT count(*) FROM conversations WHERE title='Tong quan nong trai mau AgriCore';")
    AssistantGenerations = [int](Invoke-SeedSqlScalar "agricore_assistant" `
        "SELECT count(*) FROM chat_generations WHERE idempotency_key='seed-farm-overview-v1';")
  }
  Write-Host ("Authoritative dataset: " + (($summary.GetEnumerator() |
          ForEach-Object { "$($_.Key)=$($_.Value)" }) -join " "))
}
