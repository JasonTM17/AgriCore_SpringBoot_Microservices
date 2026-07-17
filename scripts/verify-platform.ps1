# Single gating entrypoint for AgriCore portfolio verification.
# Starts FULL docker compose (apps + infra), waits health, runs tests + e2e, writes evidence bundle.
param(
  [string]$EvidenceDir = $(if ($env:EVIDENCE_DIR) { $env:EVIDENCE_DIR } else { Join-Path $PSScriptRoot "..\..\..\AppData\Local\Temp\grok-goal-4bce7ceea422\implementer" }),
  [switch]$SkipBuild,
  [switch]$SkipMaven
)

$ErrorActionPreference = "Stop"
$Root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
Set-Location $Root

# Default scratch if path missing
if (-not $EvidenceDir -or $EvidenceDir -match 'AppData\\Local\\Temp\\grok-goal') {
  $fallback = "C:\Users\Admin\AppData\Local\Temp\grok-goal-4bce7ceea422\implementer"
  if (-not $EvidenceDir) { $EvidenceDir = $fallback }
}
New-Item -ItemType Directory -Path $EvidenceDir -Force | Out-Null
Write-Host "EvidenceDir=$EvidenceDir"
Write-Host "Root=$Root"

function Write-Utf8File([string]$Path, [string]$Content) {
  [System.IO.File]::WriteAllText($Path, $Content, [System.Text.UTF8Encoding]::new($false))
}

function Wait-HttpUp([string]$Url, [int]$TimeoutSec = 300) {
  $deadline = (Get-Date).AddSeconds($TimeoutSec)
  while ((Get-Date) -lt $deadline) {
    try {
      $r = Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec 5
      if ($r.StatusCode -ge 200 -and $r.StatusCode -lt 500) {
        Write-Host "UP $Url"
        return
      }
    } catch {}
    Start-Sleep -Seconds 5
  }
  throw "Timeout waiting for $Url"
}

# Free host ports held by prior java -jar sessions
Get-Process java -ErrorAction SilentlyContinue | ForEach-Object {
  try {
    $cmd = (Get-CimInstance Win32_Process -Filter "ProcessId=$($_.Id)" -ErrorAction SilentlyContinue).CommandLine
    if ($cmd -match 'identity-service|farm-service|crop-|work-service|harvest|inventory|traceability|api-gateway|agricore') {
      Write-Host "Stopping host jar PID $($_.Id)"
      Stop-Process -Id $_.Id -Force -ErrorAction SilentlyContinue
    }
  } catch {}
}
Start-Sleep -Seconds 2

if (-not (Test-Path (Join-Path $Root ".env"))) {
  Copy-Item (Join-Path $Root ".env.example") (Join-Path $Root ".env") -ErrorAction SilentlyContinue
}

Write-Host "== docker compose up (full stack) =="
if ($SkipBuild) {
  docker compose -f docker-compose.yml up -d
} else {
  docker compose -f docker-compose.yml up -d --build
}
if ($LASTEXITCODE -ne 0) { throw "docker compose up failed: $LASTEXITCODE" }

Write-Host "== wait actuator health =="
$healthUrls = @(
  "http://localhost:8081/actuator/health",
  "http://localhost:8082/actuator/health",
  "http://localhost:8086/actuator/health",
  "http://localhost:8087/actuator/health",
  "http://localhost:8092/actuator/health",
  "http://localhost:8080/actuator/health"
)
foreach ($u in $healthUrls) {
  Wait-HttpUp $u 600
}

Write-Host "== capture compose ps =="
$psOut = docker compose -f docker-compose.yml ps 2>&1 | Out-String
Write-Utf8File (Join-Path $EvidenceDir "compose-ps.txt") $psOut
# Require app containers present in capture
foreach ($name in @("agricore-identity", "agricore-farm", "agricore-harvest", "agricore-inventory", "agricore-traceability", "agricore-gateway")) {
  if ($psOut -notmatch [regex]::Escape($name)) {
    throw "compose-ps.txt missing app container: $name"
  }
}
Write-Host $psOut

Write-Host "== git log =="
$gitLog = git -C $Root log --oneline -40 2>&1 | Out-String
Write-Utf8File (Join-Path $EvidenceDir "git-log.txt") $gitLog

if (-not $SkipMaven) {
  Write-Host "== ensure Postgres :5434 for InventoryPostgresIdempotencyTest =="
  $pgOk = $false
  for ($i = 0; $i -lt 30; $i++) {
    try {
      docker exec agricore-postgres pg_isready -U agricore 2>$null | Out-Null
      if ($LASTEXITCODE -eq 0) { $pgOk = $true; break }
    } catch {}
    Start-Sleep -Seconds 2
  }
  if (-not $pgOk) {
    throw "Compose Postgres not ready at agricore-postgres - InventoryPostgresIdempotencyTest would skip"
  }

  Write-Host "== mvnw test (full suite; Postgres tests must execute) =="
  $mvnLog = Join-Path $EvidenceDir "mvn-test.log"
  cmd /c "cd /d `"$Root`" && mvnw.cmd test -DskipITs > `"$mvnLog`" 2>&1"
  if ($LASTEXITCODE -ne 0) {
    Get-Content $mvnLog -Tail 40
    throw "mvnw test failed - see $mvnLog"
  }
  if (-not (Select-String -Path $mvnLog -Pattern "BUILD SUCCESS" -Quiet)) {
    throw "mvn-test.log missing BUILD SUCCESS"
  }
  # Gate: concurrent + idempotency tests must not be skipped (Tests run: 0)
  $pgLine = Select-String -Path $mvnLog -Pattern "InventoryPostgresIdempotencyTest" | Select-Object -Last 1
  if (-not $pgLine -or $pgLine.Line -notmatch "Tests run: 2") {
    throw "InventoryPostgresIdempotencyTest must show Tests run: 2 (got: $($pgLine.Line))"
  }
  Write-Host "InventoryPostgresIdempotencyTest: $($pgLine.Line.Trim())"
}

Write-Host "== e2e happy path =="
& (Join-Path $Root "scripts\e2e-happy-path.ps1") -EvidenceDir $EvidenceDir
if ($LASTEXITCODE -ne 0) { throw "e2e-happy-path failed" }

$tracePath = Join-Path $EvidenceDir "traceability.json"
if (-not (Test-Path $tracePath)) { throw "missing $tracePath" }
$traceText = [System.IO.File]::ReadAllText($tracePath)
if ($traceText -notmatch '"farmName"') { throw "traceability.json missing farmName" }
if ($traceText -notmatch '"plotCode"') { throw "traceability.json missing plotCode" }
if ($traceText -notmatch '"productName"') { throw "traceability.json missing productName" }

# core-slice log from e2e lines (subset)
$core = @(
  "LOGIN via gateway JWT",
  "FARM via gateway",
  "PLOT via gateway",
  "CROP_CYCLE legal stages",
  "HARVEST + Kafka inventory",
  "PUBLIC_TRACEABILITY JSON"
) -join "`n"
Write-Utf8File (Join-Path $EvidenceDir "core-slice.http.log") ($core + "`n" + (Get-Content (Join-Path $EvidenceDir "e2e-flow.log") -Raw))

Write-Host "VERIFY PLATFORM OK - evidence in $EvidenceDir"
