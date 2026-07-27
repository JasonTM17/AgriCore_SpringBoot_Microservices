# Deterministic MQTT telemetry simulator wrapper for the Docker Compose profile.
param(
  [ValidateRange(1, 1000)][int]$DeviceCount = 1,
  [ValidateRange(1, 100000)][int]$Iterations = 1,
  [ValidateRange(0, 86400)][int]$FrequencySeconds = 5,
  [double]$MinimumValue = 30,
  [double]$MaximumValue = 70,
  [double]$AnomalyMinimumValue = 5,
  [double]$AnomalyMaximumValue = 18,
  [ValidateRange(0, 100)][int]$AnomalyProbabilityPercent = 20,
  [ValidateRange(0, 2147)][int]$Seed = 42,
  [string]$DevicePrefix = "DEMO-SOIL",
  [string]$PlotMap = "DEMO-SOIL-001=PLOT-DEMO-001",
  [string]$RecordedAt = $env:MQTT_RECORDED_AT,
  [string]$DevicePasswordSeed = $env:MQTT_DEVICE_PASSWORD_SEED
)

if ($MinimumValue -gt $MaximumValue) {
  throw "MinimumValue must not exceed MaximumValue"
}
if ($AnomalyMinimumValue -gt $AnomalyMaximumValue) {
  throw "AnomalyMinimumValue must not exceed AnomalyMaximumValue"
}

$settings = @{
  MQTT_DEVICE_COUNT = $DeviceCount
  MQTT_ITERATIONS = $Iterations
  MQTT_INTERVAL_SECONDS = $FrequencySeconds
  MQTT_MIN_VALUE = $MinimumValue.ToString([Globalization.CultureInfo]::InvariantCulture)
  MQTT_MAX_VALUE = $MaximumValue.ToString([Globalization.CultureInfo]::InvariantCulture)
  MQTT_ANOMALY_MIN_VALUE = $AnomalyMinimumValue.ToString([Globalization.CultureInfo]::InvariantCulture)
  MQTT_ANOMALY_MAX_VALUE = $AnomalyMaximumValue.ToString([Globalization.CultureInfo]::InvariantCulture)
  MQTT_ANOMALY_PROBABILITY_PERCENT = $AnomalyProbabilityPercent
  MQTT_SEED = $Seed
  MQTT_DEVICE_PREFIX = $DevicePrefix
  MQTT_PLOT_MAP = $PlotMap
}
$mappedUsers = @($PlotMap -split ',' | ForEach-Object {
  if ($_ -match '=') { ($_ -split '=', 2)[0] }
})
$deviceUsers = for ($index = 1; $index -le $DeviceCount; $index++) {
  if ($index -le $mappedUsers.Count -and $mappedUsers[$index - 1]) {
    $mappedUsers[$index - 1]
  } else {
    "{0}-{1:D3}" -f $DevicePrefix, $index
  }
}
$settings.MQTT_DEVICE_USERS = $deviceUsers -join ','
if ($RecordedAt) { $settings.MQTT_RECORDED_AT = $RecordedAt }
if ($DevicePasswordSeed) { $settings.MQTT_DEVICE_PASSWORD_SEED = $DevicePasswordSeed }

$previous = @{}
$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
Push-Location $repoRoot
try {
  foreach ($entry in $settings.GetEnumerator()) {
    $previous[$entry.Key] = [Environment]::GetEnvironmentVariable($entry.Key, "Process")
    [Environment]::SetEnvironmentVariable($entry.Key, [string]$entry.Value, "Process")
  }
  & docker compose -f docker-compose.yml -f docker-compose.mqtt-simulator.yml up -d mqtt
  if ($LASTEXITCODE -ne 0) {
    throw "MQTT broker provisioning exited with code $LASTEXITCODE"
  }
  Write-Host "Publishing deterministic MQTT telemetry for $DeviceCount device(s), $Iterations iteration(s)."
  & docker compose -f docker-compose.yml -f docker-compose.mqtt-simulator.yml `
    --profile simulator run --rm iot-mqtt-simulator
  if ($LASTEXITCODE -ne 0) {
    throw "MQTT simulator exited with code $LASTEXITCODE"
  }
} finally {
  foreach ($entry in $previous.GetEnumerator()) {
    [Environment]::SetEnvironmentVariable($entry.Key, $entry.Value, "Process")
  }
  Pop-Location
}
