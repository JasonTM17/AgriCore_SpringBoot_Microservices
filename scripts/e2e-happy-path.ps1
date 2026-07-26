# AgriCore happy-path via API Gateway + real JWT (AGRICORE_DEV_MODE=false compatible).
# Optional: -EvidenceDir <path> writes UTF-8 logs + traceability.json for verifier gating.
param(
  [string]$EvidenceDir = "",
  [string]$Gateway = $(if ($env:GATEWAY_URL) { $env:GATEWAY_URL } else { "http://localhost:8080" }),
  [string]$Identity = $(if ($env:IDENTITY_URL) { $env:IDENTITY_URL } else { "http://localhost:8081" }),
  [string]$TraceDirect = $(if ($env:TRACEABILITY_URL) { $env:TRACEABILITY_URL } else { "http://localhost:8092" }),
  [string]$FarmDirect = $(if ($env:FARM_URL) { $env:FARM_URL } else { "http://localhost:8082" })
)

$ErrorActionPreference = "Stop"
$script:TranscriptLines = New-Object System.Collections.Generic.List[string]

function Log([string]$Message) {
  Write-Host $Message
  $script:TranscriptLines.Add($Message) | Out-Null
}

function Write-Utf8File([string]$Path, [string]$Content) {
  $dir = Split-Path -Parent $Path
  if ($dir -and -not (Test-Path $dir)) {
    New-Item -ItemType Directory -Path $dir -Force | Out-Null
  }
  [System.IO.File]::WriteAllText($Path, $Content, [System.Text.UTF8Encoding]::new($false))
}

function PostJson([string]$Url, [hashtable]$Headers, [hashtable]$Body) {
  return Invoke-RestMethod -Method Post -Uri $Url -Headers $Headers -Body ($Body | ConvertTo-Json -Depth 8)
}

function GetJson([string]$Url, [hashtable]$Headers = @{}) {
  return Invoke-RestMethod -Method Get -Uri $Url -Headers $Headers
}

function AssertUnauthorized([string]$Url) {
  $statusCode = $null
  try {
    $response = Invoke-WebRequest -Method Get -Uri $Url `
      -Headers @{ "Authorization" = "Bearer invalid-e2e-token" } -UseBasicParsing
    $statusCode = [int]$response.StatusCode
  } catch {
    if ($_.Exception.Response) {
      $statusCode = [int]$_.Exception.Response.StatusCode
    }
  }
  if ($statusCode -ne 401) {
    throw "Expected HTTP 401 for unauthenticated request to $Url, got $statusCode"
  }
  Log "UNAUTHORIZED_OK url=$Url status=401"
}

function PublishKafkaJson([string]$Topic, [string]$Payload) {
  $Payload | docker exec -i agricore-kafka /opt/kafka/bin/kafka-console-producer.sh `
    --bootstrap-server kafka:19092 --topic $Topic
  if ($LASTEXITCODE -ne 0) {
    throw "Kafka publish failed for topic=$Topic exit=$LASTEXITCODE"
  }
}

function ReadKafkaTopic([string]$Topic, [int]$TimeoutMs = 2000) {
  $output = docker exec agricore-kafka /opt/kafka/bin/kafka-console-consumer.sh `
    --bootstrap-server kafka:19092 --topic $Topic --from-beginning --timeout-ms $TimeoutMs 2>&1 | Out-String
  if ($LASTEXITCODE -ne 0 -and $output -notmatch "Processed a total") {
    throw "Kafka consume failed for topic=$Topic exit=$LASTEXITCODE output=$output"
  }
  return $output
}

if ($EvidenceDir) {
  New-Item -ItemType Directory -Path $EvidenceDir -Force | Out-Null
}

Log "== 0. Register + login (JWT) =="
AssertUnauthorized "$Gateway/api/v1/farms?size=1"
AssertUnauthorized "$FarmDirect/api/v1/farms?size=1"
$email = "e2e-mgr-$(Get-Random)@agricore.local"
$password = "Password1!"
try {
  PostJson "$Identity/api/v1/auth/register" @{ "Content-Type" = "application/json" } @{
    email = $email; password = $password; fullName = "E2E Farm Manager"
  } | Out-Null
} catch {
  # may already exist
}
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
Log "JWT issued (len=$($token.Length))"

Log "== 1. Farm + plot via gateway =="
$farmCode = "E2E-$(Get-Random)"
$farmObj = PostJson "$Gateway/api/v1/farms" $Auth @{
  code = $farmCode; name = "E2E Dak Lak Farm"; province = "Dak Lak"; totalAreaHa = 10.5
  latitude = 12.67; longitude = 108.05
}
Log "Farm id=$($farmObj.id) code=$($farmObj.code)"
$plotObj = PostJson "$Gateway/api/v1/farms/$($farmObj.id)/plots" $Auth @{
  code = "P1"; name = "Robusta Block"; areaInHectares = 1.25; soilType = "BASALT"
}
Log "Plot id=$($plotObj.id) code=$($plotObj.code)"

Log "== 2. Crop catalog =="
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
  $cropObj = @{ id = [guid]::NewGuid().ToString() }
}
$cropId = $cropObj.id
Log "Crop id=$cropId"

Log "== 3. Crop cycle + legal stage path =="
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
Log "Cycle id=$($cycleObj.id) stage=$($cycleObj.stage) status=$($cycleObj.status)"
# Illegal jump PLANNED -> COMPLETED must fail closed (409) via gateway JWT
try {
  $illegalBody = @{ stage = "COMPLETED" } | ConvertTo-Json
  $illegalResp = Invoke-WebRequest -Method Post `
    -Uri "$Gateway/api/v1/crop-cycles/$($cycleObj.id)/stage" `
    -Headers $Auth `
    -Body $illegalBody `
    -ContentType "application/json" `
    -UseBasicParsing
  throw "Expected 409 for illegal PLANNED->COMPLETED, got $($illegalResp.StatusCode) body=$($illegalResp.Content)"
} catch {
  $webEx = $_.Exception
  $statusCode = $null
  $errBody = $null
  if ($webEx.Response) {
    $statusCode = [int]$webEx.Response.StatusCode
    try {
      $reader = New-Object System.IO.StreamReader($webEx.Response.GetResponseStream())
      $errBody = $reader.ReadToEnd()
      $reader.Close()
    } catch {
      $errBody = $webEx.Message
    }
  }
  if ($statusCode -ne 409) {
    throw "Illegal stage expected HTTP 409, got status=$statusCode body=$errBody err=$($webEx.Message)"
  }
  Log "ILLEGAL_STAGE_OK status=$statusCode body=$errBody"
  if ($errBody -and ($errBody -notmatch "INVALID_STAGE|INVALID_STAGE_TRANSITION|409|Conflict|stage")) {
    Log "ILLEGAL_STAGE_BODY_NOTE code/message present in body (len=$($errBody.Length))"
  }
}
foreach ($stage in @("LAND_PREPARATION", "SOWING", "GROWING", "HARVESTING")) {
  $cycleObj = PostJson "$Gateway/api/v1/crop-cycles/$($cycleObj.id)/stage" $Auth @{ stage = $stage }
  Log "  stage -> $($cycleObj.stage) status=$($cycleObj.status)"
}

Log "== 4. Work task =="
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
Log "Work task completed id=$($taskObj.id)"

Log "== 5. Warehouse + harvest (outbox -> Kafka) =="
$whObj = PostJson "$Gateway/api/v1/inventory/warehouses" $Auth @{
  farmId = $farmObj.id; code = "WH-$(Get-Random)"; name = "E2E Warehouse"
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
Log "Harvest id=$($harvestObj.id) outboxEventId=$($harvestObj.lastOutboxEventId)"

Log "== 6. Wait for Kafka inventory consumer (DB proof) =="
$deadline = (Get-Date).AddSeconds(90)
$stocked = $false
$onHand = $null
while ((Get-Date) -lt $deadline) {
  try {
    $onHand = (docker exec agricore-postgres psql -U agricore -d agricore_inventory -t -A -c `
      "SELECT on_hand_quantity FROM inventory_items WHERE upper(sku)='COFFEE-ROBUSTA' ORDER BY created_at DESC LIMIT 1;").Trim()
    if ($onHand -and [decimal]$onHand -ge 90) {
      Log "Inventory stocked sku=COFFEE-ROBUSTA onHand=$onHand"
      $stocked = $true
      break
    }
  } catch {}
  Start-Sleep -Seconds 2
}
if (-not $stocked) {
  throw "Inventory stock not observed in DB within timeout. onHand='$onHand'"
}

Log "== 7. Public traceability QR (Kafka projection) =="
$productNameForCode = "Ca phe Robusta"
$prefix = ($productNameForCode -replace '[^A-Za-z0-9]', '').ToUpperInvariant()
if ($prefix.Length -gt 6) { $prefix = $prefix.Substring(0, 6) }
if ([string]::IsNullOrWhiteSpace($prefix)) { $prefix = "PRD" }
$suffix = ($harvestObj.id.ToString() -replace '-', '').Substring(0, 8).ToUpperInvariant()
$expectedCode = "$prefix-$suffix"
$candidates = @($expectedCode, "COFFEE-$suffix", "PRD-$suffix")
Log "Expected traceability code: $expectedCode"

$publicObj = $null
$traceCode = $null
$deadline2 = (Get-Date).AddSeconds(90)
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

Log "Public QR code=$traceCode product=$($publicObj.productName) farm=$($publicObj.farmName) plot=$($publicObj.plotCode)"

Log "== 8. Republish duplicate HarvestCompleted (Kafka idempotency) =="
$baselineOnHand = [decimal]$onHand
$republishResponse = Invoke-RestMethod -Method Post `
  -Uri "$Gateway/api/v1/harvests/$($harvestObj.id)/completion-event/republish" `
  -Headers $Auth
Log "Republish accepted eventId=$($republishResponse.eventId)"

$duplicateDeadline = (Get-Date).AddSeconds(45)
$duplicateStable = $false
while ((Get-Date) -lt $duplicateDeadline) {
  try {
    $afterRepublish = (docker exec agricore-postgres psql -U agricore -d agricore_inventory -t -A -c `
      "SELECT on_hand_quantity FROM inventory_items WHERE upper(sku)='COFFEE-ROBUSTA' ORDER BY created_at DESC LIMIT 1;").Trim()
    if ($afterRepublish -and [decimal]$afterRepublish -eq $baselineOnHand) {
      $duplicateStable = $true
      break
    }
  } catch {}
  Start-Sleep -Seconds 2
}
if (-not $duplicateStable) {
  throw "Duplicate HarvestCompleted changed inventory stock: before=$baselineOnHand after='$afterRepublish'"
}
$inventoryProcessedCount = (docker exec agricore-postgres psql -U agricore -d agricore_inventory -t -A -c `
  "SELECT count(*) FROM processed_events WHERE event_id='$($harvestObj.lastOutboxEventId)' AND consumer_name='inventory-harvest-completed';").Trim()
$traceabilityProcessedCount = (docker exec agricore-postgres psql -U agricore -d agricore_traceability -t -A -c `
  "SELECT count(*) FROM processed_events WHERE event_id='$($harvestObj.lastOutboxEventId)' AND consumer_name='traceability-harvest-completed';").Trim()
if ($inventoryProcessedCount -ne "1" -or $traceabilityProcessedCount -ne "1") {
  throw "Duplicate projection ledger mismatch: inventory=$inventoryProcessedCount traceability=$traceabilityProcessedCount"
}
Log "Duplicate projection OK onHand=$baselineOnHand inventoryLedger=$inventoryProcessedCount traceabilityLedger=$traceabilityProcessedCount"

Log "== 9. Notification event dedupe (real Kafka consumer) =="
$notificationEventId = [guid]::NewGuid().ToString()
$notificationCorrelationId = [guid]::NewGuid().ToString()
$notificationCustomerId = [guid]::NewGuid().ToString()
$notificationReservationId = [guid]::NewGuid().ToString()
$notificationOrderId = [guid]::NewGuid().ToString()
$notificationNow = [DateTime]::UtcNow.ToString("o")
$notificationPayload = @{
  eventId = $notificationEventId
  eventType = "SalesOrderConfirmed.v1"
  eventVersion = 1
  occurredAt = $notificationNow
  correlationId = $notificationCorrelationId
  producer = "sales-service"
  payload = @{
    salesOrderId = $notificationOrderId
    orderNumber = "E2E-NOTIFICATION"
    customerId = $notificationCustomerId
    inventoryItemId = [guid]::NewGuid().ToString()
    quantity = 1
    status = "CONFIRMED"
    reservationId = $notificationReservationId
    confirmedAt = $notificationNow
  }
} | ConvertTo-Json -Depth 8 -Compress
PublishKafkaJson "agricore.sales.events" $notificationPayload
PublishKafkaJson "agricore.sales.events" $notificationPayload

$notificationDeadline = (Get-Date).AddSeconds(45)
$notificationCount = "0"
while ((Get-Date) -lt $notificationDeadline) {
  try {
    $notificationCount = (docker exec agricore-postgres psql -U agricore -d agricore_notification -t -A -c `
      "SELECT count(*) FROM notifications WHERE source_event_id='$notificationEventId';").Trim()
    if ($notificationCount -eq "1") { break }
  } catch {}
  Start-Sleep -Seconds 2
}
if ($notificationCount -ne "1") {
  throw "Notification event dedupe failed: expected one notification for eventId=$notificationEventId, got=$notificationCount"
}
Log "Notification dedupe OK eventId=$notificationEventId count=$notificationCount"

Log "== 10. Invalid HarvestCompleted.v1 -> DLT =="
$invalidEventId = [guid]::NewGuid().ToString()
$invalidHarvestPayload = @{
  eventId = $invalidEventId
  eventType = "HarvestCompleted.v1"
  eventVersion = 2
  occurredAt = [DateTime]::UtcNow.ToString("o")
  correlationId = [guid]::NewGuid().ToString()
  producer = "harvest-service"
  payload = @{}
} | ConvertTo-Json -Depth 8 -Compress
PublishKafkaJson "agricore.harvest.events" $invalidHarvestPayload

$dltDeadline = (Get-Date).AddSeconds(45)
$dltMatches = 0
while ((Get-Date) -lt $dltDeadline) {
  $dltOutput = ReadKafkaTopic "agricore.harvest.events.DLT" 2000
  $dltMatches = [regex]::Matches($dltOutput, [regex]::Escape($invalidEventId)).Count
  if ($dltMatches -ge 2) { break }
  Start-Sleep -Seconds 2
}
if ($dltMatches -lt 2) {
  throw "Invalid HarvestCompleted event did not reach both projection DLT deliveries: matches=$dltMatches eventId=$invalidEventId"
}
Log "Harvest DLT OK eventId=$invalidEventId dltRecords=$dltMatches"

Log "E2E resilience path OK (duplicate projections + notification dedupe + Harvest DLT)"

if ($EvidenceDir) {
  $flowPath = Join-Path $EvidenceDir "e2e-flow.log"
  $flowText = ($script:TranscriptLines -join "`n") + "`n"
  Write-Utf8File $flowPath $flowText

  # core-slice.http.log: gateway JWT slice including illegal stage 409 body
  $corePath = Join-Path $EvidenceDir "core-slice.http.log"
  $coreLines = $script:TranscriptLines | Where-Object {
    $_ -match "JWT issued|Farm id=|Plot id=|Cycle id=|ILLEGAL_STAGE|stage ->"
  }
  Write-Utf8File $corePath (($coreLines -join "`n") + "`n")

  # Real public response body as UTF-8 JSON (gating artifact)
  $jsonPath = Join-Path $EvidenceDir "traceability.json"
  $jsonBody = $publicObj | ConvertTo-Json -Depth 8
  Write-Utf8File $jsonPath ($jsonBody + "`n")
  Log "Wrote evidence: $flowPath"
  Log "Wrote evidence: $corePath"
  Log "Wrote evidence: $jsonPath"
}
