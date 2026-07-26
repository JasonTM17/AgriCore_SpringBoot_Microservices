function Assert-SeedFreeSpace {
  param([hashtable]$Context)

  $driveNames = @("C", (Get-Item $Context.RepositoryRoot).PSDrive.Name) |
    Select-Object -Unique
  $snapshots = foreach ($driveName in $driveNames) {
    $drive = Get-PSDrive -Name $driveName -ErrorAction SilentlyContinue
    if ($drive) {
      [pscustomobject]@{
        Name = $drive.Name
        FreeGb = [math]::Round($drive.Free / 1GB, 2)
      }
    }
  }

  Write-Host ("Disk free: " + (($snapshots | ForEach-Object {
          "$($_.Name): $($_.FreeGb)GB"
        }) -join ", "))
  $lowDrive = $snapshots |
    Where-Object { $_.FreeGb -lt $Context.MinimumFreeSpaceGb } |
    Select-Object -First 1
  if ($lowDrive) {
    throw "Seed stopped: drive $($lowDrive.Name): has $($lowDrive.FreeGb)GB free; minimum is $($Context.MinimumFreeSpaceGb)GB."
  }
}

function Invoke-SeedJson {
  param(
    [ValidateSet("GET", "POST", "PATCH")]
    [string]$Method,
    [string]$Url,
    [hashtable]$Headers,
    [object]$Body
  )

  $parameters = @{
    Method = $Method
    Uri = $Url
    Headers = $Headers
  }
  if ($null -ne $Body) {
    $parameters.ContentType = "application/json"
    $parameters.Body = $Body | ConvertTo-Json -Depth 10 -Compress
  }
  try {
    return Invoke-RestMethod @parameters
  } catch {
    $statusCode = Get-SeedHttpStatusCode $_
    $detail = $_.ErrorDetails.Message
    if ([string]::IsNullOrWhiteSpace($detail) -and $_.Exception.Response) {
      try {
        $stream = $_.Exception.Response.GetResponseStream()
        if ($stream) {
          $reader = New-Object System.IO.StreamReader($stream)
          try {
            $detail = $reader.ReadToEnd()
          } finally {
            $reader.Dispose()
          }
        }
      } catch {
        # Some PowerShell HTTP implementations consume the response stream first.
      }
    }
    if ([string]::IsNullOrWhiteSpace($detail)) {
      $detail = $_.Exception.Message
    }
    $detail = ([string]$detail).Replace("`r", " ").Replace("`n", " ")
    if ($detail.Length -gt 500) {
      $detail = $detail.Substring(0, 500)
    }
    $failure = New-Object System.InvalidOperationException(
      "Seed request failed: $Method $Url HTTP=$statusCode detail=$detail",
      $_.Exception
    )
    $failure.Data["HttpStatusCode"] = $statusCode
    throw $failure
  }
}

function Get-SeedHttpStatusCode {
  param([System.Management.Automation.ErrorRecord]$Failure)

  if ($Failure.Exception.Data.Contains("HttpStatusCode")) {
    return [int]$Failure.Exception.Data["HttpStatusCode"]
  }
  if ($Failure.Exception.Response -and $Failure.Exception.Response.StatusCode) {
    return [int]$Failure.Exception.Response.StatusCode
  }
  return 0
}

function Invoke-SeedSqlScalar {
  param(
    [string]$Database,
    [string]$Sql
  )

  $output = & docker exec agricore-postgres psql `
    -v ON_ERROR_STOP=1 -U agricore -d $Database -Atc $Sql
  if ($LASTEXITCODE -ne 0) {
    throw "Seed database lookup failed for database=$Database."
  }
  return (($output | Out-String).Trim())
}

function Add-SeedMutation {
  param([hashtable]$Context)

  $Context.Mutations = [int]$Context.Mutations + 1
  if ($Context.DelayMilliseconds -gt 0) {
    Start-Sleep -Milliseconds $Context.DelayMilliseconds
  }
  if (($Context.Mutations % 100) -eq 0) {
    Assert-SeedFreeSpace $Context
  }
}

function New-SeedDeterministicGuid {
  param([string]$Name)

  $sha256 = [System.Security.Cryptography.SHA256]::Create()
  try {
    $bytes = [System.Text.Encoding]::UTF8.GetBytes("agricore-seed-v1:$Name")
    $hex = ([BitConverter]::ToString($sha256.ComputeHash($bytes))).Replace("-", "").ToLowerInvariant()
  } finally {
    $sha256.Dispose()
  }
  return "$($hex.Substring(0, 8))-$($hex.Substring(8, 4))-4$($hex.Substring(13, 3))-a$($hex.Substring(17, 3))-$($hex.Substring(20, 12))"
}

function Invoke-SeedAttachmentUpload {
  param(
    [string]$Url,
    [string]$Token,
    [string]$FilePath
  )

  Add-Type -AssemblyName System.Net.Http
  $client = New-Object System.Net.Http.HttpClient
  $multipart = New-Object System.Net.Http.MultipartFormDataContent
  $stream = $null
  $fileContent = $null
  try {
    $client.DefaultRequestHeaders.Authorization =
      New-Object System.Net.Http.Headers.AuthenticationHeaderValue -ArgumentList "Bearer", $Token
    $stream = [System.IO.File]::OpenRead($FilePath)
    $fileContent = New-Object System.Net.Http.StreamContent -ArgumentList (, $stream)
    $fileContent.Headers.ContentType =
      New-Object System.Net.Http.Headers.MediaTypeHeaderValue -ArgumentList "image/webp"
    $multipart.Add($fileContent, "file", [System.IO.Path]::GetFileName($FilePath))
    $response = $client.PostAsync($Url, $multipart).GetAwaiter().GetResult()
    $responseBody = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
    if (-not $response.IsSuccessStatusCode) {
      throw "Attachment upload failed: HTTP $([int]$response.StatusCode) body=$responseBody"
    }
    return $responseBody | ConvertFrom-Json
  } finally {
    if ($fileContent) { $fileContent.Dispose() }
    if ($stream) { $stream.Dispose() }
    $multipart.Dispose()
    $client.Dispose()
  }
}

function Wait-SeedCondition {
  param(
    [scriptblock]$Condition,
    [string]$FailureMessage,
    [int]$TimeoutSeconds = 90
  )

  $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
  do {
    try {
      if (& $Condition) {
        return
      }
    } catch {
      # Event projections can be temporarily unavailable while consumers catch up.
    }
    Start-Sleep -Seconds 1
  } while ((Get-Date) -lt $deadline)

  throw $FailureMessage
}

function ConvertTo-SeedSqlUuidList {
  param([object[]]$Values)

  $validated = foreach ($value in $Values) {
    $parsed = [guid]::Empty
    if (-not [guid]::TryParse([string]$value, [ref]$parsed)) {
      throw "Seed expected UUID but received '$value'."
    }
    "'$parsed'"
  }
  return ($validated -join ",")
}
