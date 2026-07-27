function Initialize-SeedIdentity {
  param([hashtable]$Context)

  Write-Host "Seeding local identity users..."
  foreach ($user in $Context.Users) {
    try {
      Invoke-SeedJson POST "$($Context.Identity)/api/v1/auth/register" `
        @{ "Content-Type" = "application/json" } @{
        email = $user.email
        password = $Context.SeedPassword
        fullName = $user.fullName
      } | Out-Null
      Add-SeedMutation $Context
      Write-Host "  registered $($user.email)"
    } catch {
      if ((Get-SeedHttpStatusCode $_) -ne 409) { throw }
      Write-Host "  reused $($user.email)"
    }

    if ($user.email -notmatch '^[a-z0-9._@-]+$') {
      throw "Seed user email contains unsupported characters."
    }
    foreach ($role in $user.roles) {
      if ($role -notmatch '^[A-Z_]+$') {
        throw "Seed role contains unsupported characters."
      }
    }
    $roleList = ($user.roles | ForEach-Object { "'$_'" }) -join ","
    & docker exec agricore-postgres psql -v ON_ERROR_STOP=1 `
      -U agricore -d agricore_identity -c `
      "INSERT INTO user_roles (user_id, role_id) SELECT u.id, r.id FROM users u CROSS JOIN roles r WHERE u.email='$($user.email)' AND r.code IN ($roleList) ON CONFLICT DO NOTHING;" |
      Out-Null
    if ($LASTEXITCODE -ne 0) {
      throw "Failed to grant local roles for $($user.email)."
    }
  }

  $login = Invoke-SeedJson POST "$($Context.Gateway)/api/v1/auth/login" `
    @{ "Content-Type" = "application/json" } @{
    email = "manager@agricore.local"
    password = $Context.SeedPassword
  }
  $token = if ($login.accessToken) { $login.accessToken } else { $login.access_token }
  if (-not $token) {
    throw "Login failed for manager@agricore.local."
  }
  $Context.Token = $token
  $Context.AuthHeaders = @{
    "Content-Type" = "application/json"
    "Authorization" = "Bearer $token"
  }
  $Context.WorkerId = Invoke-SeedSqlScalar "agricore_identity" `
    "SELECT id FROM users WHERE email='worker@agricore.local';"
  if (-not $Context.WorkerId) {
    throw "Seed worker identity was not found."
  }
}

function Sync-SeedFarmFoundation {
  param([hashtable]$Context)

  Write-Host "Seeding farms and plots..."
  $farmPage = Invoke-SeedJson GET `
    "$($Context.Gateway)/api/v1/farms?page=0&size=100&sort=code,asc" `
    $Context.AuthHeaders $null
  $farmByCode = @{}
  @($farmPage.content) | ForEach-Object { $farmByCode[$_.code] = $_ }

  $primaryPlots = New-Object System.Collections.Generic.List[object]
  foreach ($farmDefinition in $Context.PlannedFarms) {
    $farm = $farmByCode[$farmDefinition.code]
    if (-not $farm) {
      $farm = Invoke-SeedJson POST "$($Context.Gateway)/api/v1/farms" `
        $Context.AuthHeaders $farmDefinition
      $farmByCode[$farm.code] = $farm
      $Context.Counts.FarmsCreated++
      Add-SeedMutation $Context
    } else {
      $Context.Counts.FarmsReused++
    }

    $plotPage = Invoke-SeedJson GET `
      "$($Context.Gateway)/api/v1/farms/$($farm.id)/plots?page=0&size=100&sort=code,asc" `
      $Context.AuthHeaders $null
    $plotByCode = @{}
    @($plotPage.content) | ForEach-Object { $plotByCode[$_.code] = $_ }
    $primaryPlot = $null

    for ($plotIndex = 0; $plotIndex -lt $Context.PlotsPerFarm; $plotIndex++) {
      $plotNumber = $plotIndex + 1
      $plotCode = ($farmDefinition.code -replace "^FARM-", "") +
        "-P{0:D2}" -f $plotNumber
      $plot = $plotByCode[$plotCode]
      if ($plot) {
        $Context.Counts.PlotsReused++
      } else {
        $name = $Context.PlotNames[$plotIndex % $Context.PlotNames.Count]
        if ($plotIndex -ge $Context.PlotNames.Count) {
          $name = "$name $([math]::Floor($plotIndex / $Context.PlotNames.Count) + 1)"
        }
        $plot = Invoke-SeedJson POST `
          "$($Context.Gateway)/api/v1/farms/$($farm.id)/plots" `
          $Context.AuthHeaders @{
          code = $plotCode
          name = $name
          areaInHectares = [math]::Round(2.5 + (($plotIndex % 8) * 0.375), 4)
          soilType = $Context.SoilTypes[$plotIndex % $Context.SoilTypes.Count]
        }
        $plotByCode[$plotCode] = $plot
        $Context.Counts.PlotsCreated++
        Add-SeedMutation $Context
      }
      if ($plotIndex -eq 0) {
        $primaryPlot = $plot
      }
    }

    if (-not $primaryPlot) {
      throw "Primary plot missing for farm $($farm.code)."
    }
    $primaryPlots.Add([pscustomobject]@{
        Farm = $farm
        Plot = $primaryPlot
      }) | Out-Null
    Write-Host "  farm=$($farm.code) primaryPlot=$($primaryPlot.code)"
  }

  $Context.PrimaryPlots = $primaryPlots.ToArray()
}
