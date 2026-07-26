# Seed deterministic, cross-service AgriCore demo data through authenticated
# contracts. Direct database access is limited to local role bootstrap,
# idempotent entity lookup where no read API exists, and final evidence counts.
param(
  [ValidateSet("Quick", "Showcase", "Large", "Smoke", "Demo")]
  [string]$Profile = "Showcase",
  [string]$Gateway = $(if ($env:GATEWAY_URL) { $env:GATEWAY_URL } else { "http://localhost:3000" }),
  [string]$Identity = $(if ($env:IDENTITY_URL) { $env:IDENTITY_URL } else { "http://localhost:8081" }),
  [ValidateRange(0, 32)]
  [int]$FarmLimit = 0,
  [ValidateRange(0, 24)]
  [int]$PlotsPerFarm = 0,
  [ValidateRange(0, 32)]
  [int]$DomainFarmLimit = 0,
  [ValidateRange(0, 4)]
  [int]$TasksPerFarm = 0,
  [ValidateRange(0, 100)]
  [int]$ReadingsPerDevice = 0,
  [ValidateRange(0, 32)]
  [int]$SalesOrderLimit = 0,
  [ValidateRange(-1, 5000)]
  [int]$DelayMilliseconds = -1,
  [ValidateRange(1, 100)]
  [double]$MinimumFreeSpaceGb = 2,
  [switch]$DryRun
)
$ErrorActionPreference = "Stop"

$profileDefaults = @{
  Quick = @{
    Farms = 2; Plots = 3; DomainFarms = 1; Tasks = 1
    Readings = 2; SalesOrders = 1; Delay = 0
  }
  Showcase = @{
    Farms = 8; Plots = 6; DomainFarms = 4; Tasks = 2
    Readings = 5; SalesOrders = 4; Delay = 10
  }
  Large = @{
    Farms = 32; Plots = 24; DomainFarms = 32; Tasks = 4
    Readings = 20; SalesOrders = 16; Delay = 25
  }
}
$Profile = switch ($Profile) {
  "Smoke" { "Quick" }
  "Demo" { "Showcase" }
  default { $Profile }
}
$selectedProfile = $profileDefaults[$Profile]
if ($FarmLimit -eq 0) { $FarmLimit = $selectedProfile.Farms }
if ($PlotsPerFarm -eq 0) { $PlotsPerFarm = $selectedProfile.Plots }
if ($DomainFarmLimit -eq 0) {
  $DomainFarmLimit = [math]::Min($selectedProfile.DomainFarms, $FarmLimit)
}
if ($TasksPerFarm -eq 0) { $TasksPerFarm = $selectedProfile.Tasks }
if ($ReadingsPerDevice -eq 0) { $ReadingsPerDevice = $selectedProfile.Readings }
if ($SalesOrderLimit -eq 0) {
  $SalesOrderLimit = [math]::Min($selectedProfile.SalesOrders, $DomainFarmLimit)
}
if ($DelayMilliseconds -lt 0) { $DelayMilliseconds = $selectedProfile.Delay }
if ($DomainFarmLimit -gt $FarmLimit) {
  throw "DomainFarmLimit cannot exceed FarmLimit."
}
if ($SalesOrderLimit -gt $DomainFarmLimit) {
  throw "SalesOrderLimit cannot exceed DomainFarmLimit."
}

$repositoryRoot = Split-Path -Parent $PSScriptRoot
. "$PSScriptRoot/seed/seed-support.ps1"
. "$PSScriptRoot/seed/seed-foundation.ps1"
. "$PSScriptRoot/seed/seed-production-flow.ps1"
. "$PSScriptRoot/seed/seed-connected-experiences.ps1"

$users = @(
  @{ email = "admin@agricore.local"; fullName = "System Admin"; roles = @("SYSTEM_ADMIN") },
  @{
    email = "manager@agricore.local"
    fullName = "Farm Manager"
    roles = @("FARM_MANAGER", "AGRONOMIST", "WAREHOUSE_MANAGER", "SALES_STAFF")
  },
  @{ email = "agronomist@agricore.local"; fullName = "Agronomist"; roles = @("AGRONOMIST") },
  @{ email = "worker@agricore.local"; fullName = "Field Worker"; roles = @("FIELD_WORKER") },
  @{ email = "warehouse@agricore.local"; fullName = "Warehouse Manager"; roles = @("WAREHOUSE_MANAGER") },
  @{ email = "sales@agricore.local"; fullName = "Sales Staff"; roles = @("SALES_STAFF") },
  @{ email = "auditor@agricore.local"; fullName = "Auditor"; roles = @("AUDITOR") }
)
$regionTemplates = @(
  @{ Prefix = "DL"; Province = "Dak Lak"; Area = 120.5; Latitude = 12.6667; Longitude = 108.05 },
  @{ Prefix = "LD"; Province = "Lam Dong"; Area = 80.0; Latitude = 11.94; Longitude = 108.44 },
  @{ Prefix = "BT"; Province = "Binh Thuan"; Area = 95.0; Latitude = 10.93; Longitude = 108.10 },
  @{ Prefix = "LA"; Province = "Long An"; Area = 64.0; Latitude = 10.53; Longitude = 106.41 },
  @{ Prefix = "CT"; Province = "Can Tho"; Area = 72.5; Latitude = 10.045; Longitude = 105.746 },
  @{ Prefix = "AG"; Province = "An Giang"; Area = 110.0; Latitude = 10.52; Longitude = 105.125 },
  @{ Prefix = "QN"; Province = "Quang Nam"; Area = 88.0; Latitude = 15.54; Longitude = 108.02 },
  @{ Prefix = "SL"; Province = "Son La"; Area = 76.0; Latitude = 21.33; Longitude = 103.91 }
)
$plannedFarms = for ($index = 0; $index -lt $FarmLimit; $index++) {
  $template = $regionTemplates[$index % $regionTemplates.Count]
  $wave = [int]([math]::Floor($index / $regionTemplates.Count) + 1)
  @{
    code = "FARM-{0}-{1:D2}" -f $template.Prefix, $wave
    name = "Nong trai {0} {1:D2}" -f $template.Province, $wave
    province = $template.Province
    totalAreaHa = [math]::Round($template.Area + (($wave - 1) * 1.25), 4)
    latitude = [math]::Round($template.Latitude + (($wave - 1) * 0.005), 6)
    longitude = [math]::Round($template.Longitude + (($wave - 1) * 0.005), 6)
  }
}
$mediaFiles = @(
  "$repositoryRoot/assets/media/agricore-showcase/agricore-farm-sunrise.webp",
  "$repositoryRoot/assets/media/agricore-showcase/agricore-harvest-packing.webp",
  "$repositoryRoot/assets/media/agricore-showcase/agricore-traceability-produce.webp"
)
foreach ($mediaFile in $mediaFiles) {
  if (-not (Test-Path $mediaFile -PathType Leaf)) {
    throw "Required seed media is missing: $mediaFile"
  }
}

$context = @{
  RepositoryRoot = $repositoryRoot
  Profile = $Profile
  Gateway = $Gateway.TrimEnd("/")
  Identity = $Identity.TrimEnd("/")
  FarmLimit = $FarmLimit
  PlotsPerFarm = $PlotsPerFarm
  DomainFarmLimit = $DomainFarmLimit
  TasksPerFarm = $TasksPerFarm
  ReadingsPerDevice = $ReadingsPerDevice
  SalesOrderLimit = $SalesOrderLimit
  DelayMilliseconds = $DelayMilliseconds
  MinimumFreeSpaceGb = $MinimumFreeSpaceGb
  PlannedFarms = @($plannedFarms)
  Users = $users
  MediaFiles = $mediaFiles
  PlotNames = @(
    "Khu Bac", "Khu Nam", "Nha Luoi", "Vuon Uom", "Khu Dong", "Khu Tay",
    "Khu Trung", "Khu Phoi Tron", "Khu Dong Goi", "Khu Cach Ly",
    "Khu Thu Nghiem", "Khu Giong"
  )
  SoilTypes = @("BASALT", "ALLUVIAL", "LOAM", "SANDY_LOAM")
  Mutations = 0
  Counts = [ordered]@{
    FarmsCreated = 0; FarmsReused = 0; PlotsCreated = 0; PlotsReused = 0
    CyclesCreated = 0; CyclesReused = 0; TasksCreated = 0; TasksReused = 0
    AttachmentsUploaded = 0; WarehousesCreated = 0; WarehousesReused = 0
    HarvestsCreated = 0; HarvestsReused = 0; DevicesCreated = 0; DevicesReused = 0
    ReadingsSynchronized = 0; CustomersCreated = 0; CustomersReused = 0
    OrdersCreated = 0; OrdersReused = 0; ConversationsCreated = 0
    ConversationsReused = 0
  }
}

$plannedPlots = $FarmLimit * $PlotsPerFarm
$plannedTasks = $DomainFarmLimit * $TasksPerFarm
$plannedReadings = $DomainFarmLimit * $ReadingsPerDevice
Write-Host "AgriCore seed profile=$Profile farms=$FarmLimit plots=$plannedPlots domainFarms=$DomainFarmLimit tasks=$plannedTasks readings=$plannedReadings salesOrders=$SalesOrderLimit delayMs=$DelayMilliseconds"
Assert-SeedFreeSpace $context
if ($DryRun) {
  Write-Host "Dry run only. Cross-service plan includes cycles, work tasks, image attachments, warehouses, harvest projections, IoT readings, sales sagas, notifications, and one assistant conversation. No API or database calls made."
  exit 0
}

$context.SeedPassword = $env:AGRICORE_SEED_PASSWORD
if ([string]::IsNullOrWhiteSpace($context.SeedPassword)) {
  throw "Set AGRICORE_SEED_PASSWORD for local demo users. The script never stores or prints it."
}
if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
  throw "Docker CLI is required for local identity bootstrap and evidence queries."
}

$startedAt = Get-Date
Initialize-SeedIdentity $context
Sync-SeedFarmFoundation $context
Sync-SeedProductionFlows $context
Sync-SeedConnectedExperiences $context
Assert-SeedFreeSpace $context

$elapsed = [math]::Round(((Get-Date) - $startedAt).TotalSeconds, 2)
Write-Host "Seed complete in ${elapsed}s; synchronizedCalls=$($context.Mutations)."
Write-Host ("Created/reused: " + (($context.Counts.GetEnumerator() |
        ForEach-Object { "$($_.Key)=$($_.Value)" }) -join " "))
Write-Host "Local demo login: manager@agricore.local (password read from AGRICORE_SEED_PASSWORD; never printed)."
