# Seed deterministic AgriCore demo data through authenticated service contracts.
# Identity role grants are the only direct database operation because the public
# registration endpoint intentionally cannot bootstrap privileged local users.
param(
  [ValidateSet("Quick", "Showcase", "Large")]
  [string]$Profile = "Showcase",
  [string]$Gateway = $(if ($env:GATEWAY_URL) { $env:GATEWAY_URL } else { "http://localhost:8080" }),
  [string]$Identity = $(if ($env:IDENTITY_URL) { $env:IDENTITY_URL } else { "http://localhost:8081" }),
  [ValidateRange(0, 32)]
  [int]$FarmLimit = 0,
  [ValidateRange(0, 24)]
  [int]$PlotsPerFarm = 0,
  [ValidateRange(-1, 5000)]
  [int]$DelayMilliseconds = -1,
  [ValidateRange(1, 100)]
  [double]$MinimumFreeSpaceGb = 2,
  [switch]$DryRun
)
$ErrorActionPreference = "Stop"

$profileDefaults = @{
  Quick = @{ Farms = 2; Plots = 3; Delay = 0 }
  Showcase = @{ Farms = 8; Plots = 6; Delay = 10 }
  Large = @{ Farms = 32; Plots = 24; Delay = 25 }
}
$selectedProfile = $profileDefaults[$Profile]
if ($FarmLimit -eq 0) { $FarmLimit = $selectedProfile.Farms }
if ($PlotsPerFarm -eq 0) { $PlotsPerFarm = $selectedProfile.Plots }
if ($DelayMilliseconds -lt 0) { $DelayMilliseconds = $selectedProfile.Delay }

$users = @(
  @{ email = "admin@agricore.local"; fullName = "System Admin"; roles = @("SYSTEM_ADMIN") },
  @{ email = "manager@agricore.local"; fullName = "Farm Manager"; roles = @("FARM_MANAGER", "AGRONOMIST") },
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
$plotNames = @(
  "Khu Bac", "Khu Nam", "Nha Luoi", "Vuon Uom", "Khu Dong", "Khu Tay",
  "Khu Trung", "Khu Phoi Tron", "Khu Dong Goi", "Khu Cach Ly", "Khu Thu Nghiem", "Khu Giong"
)
$soilTypes = @("BASALT", "ALLUVIAL", "LOAM", "SANDY_LOAM")

function Assert-FreeSpace {
  $repoDrive = (Get-Item $PSScriptRoot).PSDrive.Name
  $driveNames = @("C", $repoDrive) | Select-Object -Unique
  $snapshots = foreach ($driveName in $driveNames) {
    $drive = Get-PSDrive -Name $driveName -ErrorAction SilentlyContinue
    if ($drive) {
      [pscustomobject]@{
        Name = $drive.Name
        FreeGb = [math]::Round($drive.Free / 1GB, 2)
      }
    }
  }
  Write-Host ("Disk free: " + (($snapshots | ForEach-Object { "$($_.Name): $($_.FreeGb)GB" }) -join ", "))
  $lowDrive = $snapshots | Where-Object { $_.FreeGb -lt $MinimumFreeSpaceGb } | Select-Object -First 1
  if ($lowDrive) {
    throw "Seed stopped: drive $($lowDrive.Name): has $($lowDrive.FreeGb)GB free; minimum is $MinimumFreeSpaceGb GB."
  }
}

function Invoke-Json {
  param(
    [ValidateSet("GET", "POST")]
    [string]$Method,
    [string]$Url,
    [hashtable]$Headers,
    [object]$Body
  )
  $parameters = @{ Method = $Method; Uri = $Url; Headers = $Headers }
  if ($Method -eq "POST") {
    $parameters.Body = $Body | ConvertTo-Json -Depth 8
  }
  return Invoke-RestMethod @parameters
}

function Get-HttpStatusCode {
  param([System.Management.Automation.ErrorRecord]$Failure)
  if ($Failure.Exception.Response -and $Failure.Exception.Response.StatusCode) {
    return [int]$Failure.Exception.Response.StatusCode
  }
  return 0
}

function Wait-AfterMutation {
  if ($DelayMilliseconds -gt 0) {
    Start-Sleep -Milliseconds $DelayMilliseconds
  }
}

function New-FarmDefinitions {
  $definitions = @()
  for ($index = 0; $index -lt $FarmLimit; $index++) {
    $template = $regionTemplates[$index % $regionTemplates.Count]
    $wave = [int]([math]::Floor($index / $regionTemplates.Count) + 1)
    $definitions += @{
      code = "FARM-{0}-{1:D2}" -f $template.Prefix, $wave
      name = "Nong trai {0} {1:D2}" -f $template.Province, $wave
      province = $template.Province
      totalAreaHa = [math]::Round($template.Area + (($wave - 1) * 1.25), 4)
      latitude = [math]::Round($template.Latitude + (($wave - 1) * 0.005), 6)
      longitude = [math]::Round($template.Longitude + (($wave - 1) * 0.005), 6)
    }
  }
  return $definitions
}

$plannedFarms = New-FarmDefinitions
$plannedPlots = $FarmLimit * $PlotsPerFarm
Write-Host "AgriCore seed profile=$Profile farms=$FarmLimit plotsPerFarm=$PlotsPerFarm totalPlots=$plannedPlots delayMs=$DelayMilliseconds"
Assert-FreeSpace
if ($DryRun) {
  Write-Host "Dry run only. First farm=$($plannedFarms[0].code); last farm=$($plannedFarms[-1].code); no API or database calls made."
  exit 0
}

$seedPassword = $env:AGRICORE_SEED_PASSWORD
if ([string]::IsNullOrWhiteSpace($seedPassword)) {
  throw "Set AGRICORE_SEED_PASSWORD for local demo users. The script never stores or prints it."
}
if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
  throw "Docker CLI is required to bootstrap local identity roles."
}

Write-Host "Seeding local identity users..."
foreach ($user in $users) {
  try {
    Invoke-Json POST "$Identity/api/v1/auth/register" @{ "Content-Type" = "application/json" } @{
      email = $user.email
      password = $seedPassword
      fullName = $user.fullName
    } | Out-Null
    Write-Host "  registered $($user.email)"
  } catch {
    if ((Get-HttpStatusCode $_) -ne 409) { throw }
    Write-Host "  reused $($user.email)"
  }

  $roleList = ($user.roles | ForEach-Object { "'$_'" }) -join ","
  & docker exec agricore-postgres psql -v ON_ERROR_STOP=1 -U agricore -d agricore_identity -c `
    "INSERT INTO user_roles (user_id, role_id) SELECT u.id, r.id FROM users u CROSS JOIN roles r WHERE u.email='$($user.email)' AND r.code IN ($roleList) ON CONFLICT DO NOTHING;" | Out-Null
  if ($LASTEXITCODE -ne 0) { throw "Failed to grant local roles for $($user.email)." }
}

$login = Invoke-Json POST "$Gateway/api/v1/auth/login" @{ "Content-Type" = "application/json" } @{
  email = "manager@agricore.local"
  password = $seedPassword
}
$token = if ($login.accessToken) { $login.accessToken } else { $login.access_token }
if (-not $token) { throw "Login failed for manager@agricore.local." }
$authHeaders = @{ "Content-Type" = "application/json"; "Authorization" = "Bearer $token" }

$farmPage = Invoke-Json GET "$Gateway/api/v1/farms?page=0&size=100&sort=code,asc" $authHeaders $null
$farmByCode = @{}
@($farmPage.content) | ForEach-Object { $farmByCode[$_.code] = $_ }
$createdFarms = 0
$reusedFarms = 0
$createdPlots = 0
$reusedPlots = 0
$mutations = 0

foreach ($farmDefinition in $plannedFarms) {
  $farm = $farmByCode[$farmDefinition.code]
  if (-not $farm) {
    $farm = Invoke-Json POST "$Gateway/api/v1/farms" $authHeaders $farmDefinition
    $farmByCode[$farm.code] = $farm
    $createdFarms++
    $mutations++
    Wait-AfterMutation
  } else {
    $reusedFarms++
  }
  Write-Host "  farm $($farm.code) id=$($farm.id)"

  $plotPage = Invoke-Json GET "$Gateway/api/v1/farms/$($farm.id)/plots?page=0&size=100&sort=code,asc" $authHeaders $null
  $existingPlotCodes = @{}
  @($plotPage.content) | ForEach-Object { $existingPlotCodes[$_.code] = $true }
  for ($plotIndex = 0; $plotIndex -lt $PlotsPerFarm; $plotIndex++) {
    $plotNumber = $plotIndex + 1
    $plotCode = ($farmDefinition.code -replace "^FARM-", "") + "-P{0:D2}" -f $plotNumber
    if ($existingPlotCodes.ContainsKey($plotCode)) {
      $reusedPlots++
      continue
    }
    $name = $plotNames[$plotIndex % $plotNames.Count]
    if ($plotIndex -ge $plotNames.Count) {
      $name = "$name $([math]::Floor($plotIndex / $plotNames.Count) + 1)"
    }
    Invoke-Json POST "$Gateway/api/v1/farms/$($farm.id)/plots" $authHeaders @{
      code = $plotCode
      name = $name
      areaInHectares = [math]::Round(2.5 + (($plotIndex % 8) * 0.375), 4)
      soilType = $soilTypes[$plotIndex % $soilTypes.Count]
    } | Out-Null
    $createdPlots++
    $mutations++
    Wait-AfterMutation
    if (($mutations % 100) -eq 0) { Assert-FreeSpace }
  }
}

Assert-FreeSpace
Write-Host "Seed complete: farms created=$createdFarms reused=$reusedFarms; plots created=$createdPlots reused=$reusedPlots."
Write-Host "Local demo login: manager@agricore.local (password read from AGRICORE_SEED_PASSWORD; never printed)."
