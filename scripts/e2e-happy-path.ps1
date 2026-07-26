# AgriCore happy-path via API Gateway + real JWT (AGRICORE_DEV_MODE=false compatible).
# Optional: -EvidenceDir <path> writes UTF-8 logs + traceability.json for verifier gating.
param(
  [string]$EvidenceDir = "",
  [string]$Gateway = $(if ($env:GATEWAY_URL) { $env:GATEWAY_URL } else { "http://localhost:3000" }),
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
  $previousErrorActionPreference = $ErrorActionPreference
  try {
    # Kafka's console consumer writes its normal timeout summary to stderr.
    # Capture it for validation without letting Windows PowerShell promote it
    # to a terminating NativeCommandError.
    $ErrorActionPreference = "Continue"
    $output = docker exec agricore-kafka /opt/kafka/bin/kafka-console-consumer.sh `
      --bootstrap-server kafka:19092 --topic $Topic --from-beginning --timeout-ms $TimeoutMs 2>&1 | Out-String
    $consumerExitCode = $LASTEXITCODE
  } finally {
    $ErrorActionPreference = $previousErrorActionPreference
  }
  if ($consumerExitCode -ne 0 -and $output -notmatch "Processed a total") {
    throw "Kafka consume failed for topic=$Topic exit=$consumerExitCode output=$output"
  }
  return $output
}

function GetKafkaTopicEndOffsets([string]$Topic) {
  $output = docker exec agricore-kafka /opt/kafka/bin/kafka-get-offsets.sh `
    --bootstrap-server kafka:19092 --topic $Topic --time -1
  if ($LASTEXITCODE -ne 0) {
    throw "Kafka end-offset lookup failed for topic=$Topic exit=$LASTEXITCODE"
  }
  $offsets = @{}
  foreach ($line in @($output)) {
    if ($line -match '^.+:(\d+):(\d+)$') {
      $offsets[[int]$Matches[1]] = [long]$Matches[2]
    }
  }
  if ($offsets.Count -eq 0) {
    throw "Kafka end-offset lookup returned no partitions for topic=$Topic output=$output"
  }
  return $offsets
}

function FormatKafkaOffsets([hashtable]$Offsets) {
  return (($Offsets.GetEnumerator() |
        Sort-Object Key |
        ForEach-Object { "$($_.Key):$($_.Value)" }) -join ",")
}

function WaitKafkaGroupCaughtUp(
  [string]$Group,
  [string]$Topic,
  [hashtable]$TargetOffsets,
  [int]$TimeoutSec = 60
) {
  $deadline = (Get-Date).AddSeconds($TimeoutSec)
  $lastDescription = ""
  while ((Get-Date) -lt $deadline) {
    $previousErrorActionPreference = $ErrorActionPreference
    try {
      $ErrorActionPreference = "Continue"
      $lastDescription = docker exec agricore-kafka /opt/kafka/bin/kafka-consumer-groups.sh `
        --bootstrap-server kafka:19092 --describe --group $Group 2>&1 | Out-String
      $describeExitCode = $LASTEXITCODE
    } finally {
      $ErrorActionPreference = $previousErrorActionPreference
    }
    if ($describeExitCode -eq 0) {
      $currentOffsets = @{}
      foreach ($line in ($lastDescription -split "`r?`n")) {
        $columns = @($line.Trim() -split '\s+')
        if ($columns.Count -ge 5 -and
            $columns[0] -eq $Group -and
            $columns[1] -eq $Topic -and
            $columns[2] -match '^\d+$' -and
            $columns[3] -match '^\d+$') {
          $currentOffsets[[int]$columns[2]] = [long]$columns[3]
        }
      }
      $caughtUp = $true
      foreach ($partition in $TargetOffsets.Keys) {
        $target = [long]$TargetOffsets[$partition]
        if ($target -gt 0 -and
            (-not $currentOffsets.ContainsKey([int]$partition) -or
             $currentOffsets[[int]$partition] -lt $target)) {
          $caughtUp = $false
          break
        }
      }
      if ($caughtUp) {
        Log "Kafka group caught up group=$Group topic=$Topic targets=$(FormatKafkaOffsets $TargetOffsets)"
        return
      }
    }
    Start-Sleep -Seconds 1
  }
  throw "Kafka group did not reach target offsets: group=$Group topic=$Topic targets=$(FormatKafkaOffsets $TargetOffsets) last=$lastDescription"
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
$taskObj = PostJson "$Gateway/api/v1/work-tasks/$($taskObj.id)/assign" $Auth @{
  assignedEmployeeId = [guid]::NewGuid().ToString()
}
$taskObj = Invoke-RestMethod -Method Post `
  -Uri "$Gateway/api/v1/work-tasks/$($taskObj.id)/start" `
  -Headers $Auth
if ($taskObj.status -ne "IN_PROGRESS") {
  throw "Work task must be IN_PROGRESS before completion, got '$($taskObj.status)'"
}
$startedVersion = [long]$taskObj.version
$taskObj = PostJson "$Gateway/api/v1/work-tasks/$($taskObj.id)/complete" $Auth @{ notes = "ok" }
if ($taskObj.status -ne "COMPLETED") {
  throw "Work task must be COMPLETED, got '$($taskObj.status)'"
}
if (-not $taskObj.actualEnd) {
  throw "Completed work task must include actualEnd"
}
if ([long]$taskObj.version -le $startedVersion) {
  throw "Work task completion must advance version: started=$startedVersion completed=$($taskObj.version)"
}
Log "Work task completed id=$($taskObj.id) actualEnd=$($taskObj.actualEnd) version=$($taskObj.version)"

Log "== 5. Warehouse + harvest (outbox -> Kafka) =="
$whObj = PostJson "$Gateway/api/v1/inventory/warehouses" $Auth @{
  farmId = $farmObj.id; code = "WH-$(Get-Random)"; name = "E2E Warehouse"
}
$inventoryBefore = [decimal](docker exec agricore-postgres psql -U agricore -d agricore_inventory -t -A -c `
  "SELECT COALESCE(SUM(on_hand_quantity), 0) FROM inventory_items WHERE upper(sku)='COFFEE-ROBUSTA';").Trim()
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
      "SELECT COALESCE(SUM(on_hand_quantity), 0) FROM inventory_items WHERE upper(sku)='COFFEE-ROBUSTA';").Trim()
    if ($onHand -and [decimal]$onHand -ge ($inventoryBefore + 90)) {
      Log "Inventory stocked sku=COFFEE-ROBUSTA before=$inventoryBefore onHand=$onHand"
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
$suffix = ($harvestObj.id.ToString() -replace '-', '').ToUpperInvariant()
$expectedCode = "$prefix-$suffix"
Log "Expected traceability code: $expectedCode"

$publicObj = $null
$traceCode = $null
$deadline2 = (Get-Date).AddSeconds(90)
while ((Get-Date) -lt $deadline2 -and -not $publicObj) {
  try {
    $publicObj = GetJson "$Gateway/public/api/v1/traceability/$expectedCode"
    $traceCode = $expectedCode
  } catch {
    try {
      $publicObj = GetJson "$TraceDirect/public/api/v1/traceability/$expectedCode"
      $traceCode = $expectedCode
    } catch {}
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
$completionBefore = GetJson "$Gateway/api/v1/harvests/$($harvestObj.id)/completion-event" $Auth
if ($completionBefore.state -ne "PUBLISHED") {
  throw "Initial harvest completion event must be PUBLISHED before republish, got '$($completionBefore.state)'"
}
$baselinePublishAttempts = [int]$completionBefore.publishAttempts
$republishResponse = Invoke-RestMethod -Method Post `
  -Uri "$Gateway/api/v1/harvests/$($harvestObj.id)/completion-event/republish" `
  -Headers $Auth
Log "Republish accepted eventId=$($republishResponse.eventId)"

$republishDeadline = (Get-Date).AddSeconds(90)
$completionAfter = $null
while ((Get-Date) -lt $republishDeadline) {
  try {
    $completionAfter = GetJson "$Gateway/api/v1/harvests/$($harvestObj.id)/completion-event" $Auth
    if ($completionAfter.state -eq "PUBLISHED" -and
        [int]$completionAfter.publishAttempts -gt $baselinePublishAttempts) {
      break
    }
  } catch {}
  Start-Sleep -Seconds 1
}
if (-not $completionAfter -or
    $completionAfter.state -ne "PUBLISHED" -or
    [int]$completionAfter.publishAttempts -le $baselinePublishAttempts) {
  throw "Republished harvest event was not published: state=$($completionAfter.state) attempts=$($completionAfter.publishAttempts) baseline=$baselinePublishAttempts"
}
$harvestTargetOffsets = GetKafkaTopicEndOffsets "agricore.harvest.events"
WaitKafkaGroupCaughtUp "inventory-service" "agricore.harvest.events" $harvestTargetOffsets 60
WaitKafkaGroupCaughtUp "traceability-service" "agricore.harvest.events" $harvestTargetOffsets 60
Start-Sleep -Seconds 3
$afterRepublish = (docker exec agricore-postgres psql -U agricore -d agricore_inventory -t -A -c `
  "SELECT COALESCE(SUM(on_hand_quantity), 0) FROM inventory_items WHERE upper(sku)='COFFEE-ROBUSTA';").Trim()
if (-not $afterRepublish -or [decimal]$afterRepublish -ne $baselineOnHand) {
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

$notificationTargetOffsets = GetKafkaTopicEndOffsets "agricore.sales.events"
WaitKafkaGroupCaughtUp "notification-service" "agricore.sales.events" $notificationTargetOffsets 60
$notificationCount = (docker exec agricore-postgres psql -U agricore -d agricore_notification -t -A -c `
  "SELECT count(*) FROM notifications WHERE source_event_id='$notificationEventId';").Trim()
if ($notificationCount -ne "1") {
  throw "Notification event dedupe failed: expected one notification for eventId=$notificationEventId, got=$notificationCount"
}
Start-Sleep -Seconds 3
$notificationStableCount = (docker exec agricore-postgres psql -U agricore -d agricore_notification -t -A -c `
  "SELECT count(*) FROM notifications WHERE source_event_id='$notificationEventId';").Trim()
if ($notificationStableCount -ne "1") {
  throw "Notification dedupe was not stable after consumer quiescence: eventId=$notificationEventId count=$notificationStableCount"
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
