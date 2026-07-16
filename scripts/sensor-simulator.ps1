# Simple AgriCore IoT sensor simulator (HTTP ingest)
param(
  [string]$BaseUrl = "http://localhost:8090",
  [string]$DeviceCode = "SIM-SOIL-01",
  [int]$IntervalSeconds = 5,
  [int]$Iterations = 12
)

Write-Host "Simulating device $DeviceCode -> $BaseUrl ($Iterations readings)"
for ($i = 1; $i -le $Iterations; $i++) {
  $moisture = if ((Get-Random -Maximum 100) -lt 20) { Get-Random -Minimum 5 -Maximum 18 } else { Get-Random -Minimum 30 -Maximum 70 }
  $body = @{
    deviceCode = $DeviceCode
    metricType = "SOIL_MOISTURE"
    metricValue = $moisture
    unit = "PCT"
  } | ConvertTo-Json
  try {
    $resp = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/v1/iot/readings" `
      -Headers @{ "X-Dev-User"="sim"; "X-Dev-Roles"="AGRONOMIST"; "Content-Type"="application/json" } `
      -Body $body
    Write-Host "[$i] moisture=$moisture alertRaised=$($resp.alertRaised) status=$($resp.alertStatus)"
  } catch {
    Write-Host "[$i] error: $_"
  }
  Start-Sleep -Seconds $IntervalSeconds
}
