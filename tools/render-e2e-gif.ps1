<#
.SYNOPSIS
    Renders captured e2e-happy-path output into the animated terminal GIF used by the README.

.DESCRIPTION
    The GIF in docs/images is generated from a real run, never hand-authored. This script exists so
    the asset can be regenerated after the flow changes, and so a reviewer can confirm it was not
    staged.

    Refuses to render if anything resembling a JWT appears in the captured output. The e2e script
    logs only the token's length, but an artifact published to a public repository is the wrong
    place to discover that changed.

    Requires ImageMagick 7 (`magick`) on PATH.

.EXAMPLE
    docker compose up -d
    pwsh scripts/e2e-happy-path.ps1 *>&1 | Tee-Object -FilePath e2e.txt
    pwsh tools/render-e2e-gif.ps1 -InputFile e2e.txt
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$InputFile,

    [string]$OutputGif = (Join-Path $PSScriptRoot "..\docs\images\e2e-happy-path.gif"),

    [string]$WorkDir = (Join-Path ([System.IO.Path]::GetTempPath()) "agricore-gif-frames"),

    [int]$WrapAt = 104
)

$ErrorActionPreference = "Stop"

if (-not (Get-Command magick -ErrorAction SilentlyContinue)) {
    throw "ImageMagick 7 is required: 'magick' was not found on PATH."
}

$raw = Get-Content -LiteralPath $InputFile
foreach ($line in $raw) {
    if ($line -match 'eyJ[A-Za-z0-9_\-\.]{20,}') {
        throw "Refusing to render: a JWT-like string is present in $InputFile."
    }
}

Remove-Item -Recurse -Force $WorkDir -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Path $WorkDir -Force | Out-Null
New-Item -ItemType Directory -Path (Split-Path -Parent $OutputGif) -Force | Out-Null

# Wrap so nothing runs off the canvas; continuation lines are indented.
$lines = New-Object System.Collections.Generic.List[string]
foreach ($line in $raw) {
    $text = $line.TrimEnd()
    if ($text.Length -le $WrapAt) { $lines.Add($text); continue }
    $offset = 0
    while ($offset -lt $text.Length) {
        $take = [Math]::Min($WrapAt, $text.Length - $offset)
        $chunk = $text.Substring($offset, $take)
        if ($offset -gt 0) { $chunk = "    " + $chunk }
        $lines.Add($chunk)
        $offset += $take
    }
}

# -annotate treats backslash as an escape and strips leading spaces. Double the former, and render
# the latter as no-break spaces so indentation survives.
function ConvertTo-AnnotateText([string]$text) {
    $escaped = $text -replace '\\', '\\'
    $lead = 0
    while ($lead -lt $escaped.Length -and $escaped[$lead] -eq ' ') { $lead++ }
    if ($lead -gt 0) {
        $escaped = ([string][char]0x00A0) * $lead + $escaped.Substring($lead)
    }
    return $escaped
}

function Get-LineColour([string]$text) {
    if ($text -match '^==\s') { return "#7ee787" }
    if ($text -match 'happy path OK') { return "#3fb950" }
    if ($text -match 'ILLEGAL_STAGE_OK|409|Conflict') { return "#d29922" }
    if ($text -match '^\s+stage ->') { return "#79c0ff" }
    if ($text -match 'Public QR|Inventory stocked|JWT issued') { return "#a5d6ff" }
    return "#c9d1d9"
}

$fontSize = 15
$lineH    = 21
$padX     = 22
$padTop   = 46
$width    = 960
$height   = $padTop + ($lines.Count * $lineH) + 24
$prompt   = "PS D:\AgriCore> .\scripts\e2e-happy-path.ps1"

for ($n = 0; $n -le $lines.Count; $n++) {
    $argv = @("-size", "${width}x${height}", "xc:#0d1117", "-font", "Consolas", "-pointsize", "$fontSize")
    $argv += @("-fill", "#161b22", "-draw", "rectangle 0,0 $width,32")
    $argv += @("-fill", "#ff5f56", "-draw", "circle 18,16 18,21")
    $argv += @("-fill", "#ffbd2e", "-draw", "circle 38,16 38,21")
    $argv += @("-fill", "#27c93f", "-draw", "circle 58,16 58,21")
    $argv += @("-fill", "#8b949e", "-annotate", "+80+21", "AgriCore - end-to-end happy path")
    $argv += @("-fill", "#58a6ff", "-annotate", "+$padX+$padTop", (ConvertTo-AnnotateText $prompt))

    for ($i = 0; $i -lt $n; $i++) {
        $y = $padTop + (($i + 1) * $lineH)
        $argv += @("-fill", (Get-LineColour $lines[$i]), "-annotate", "+$padX+$y", (ConvertTo-AnnotateText $lines[$i]))
    }

    $argv += (Join-Path $WorkDir ("frame_{0:D3}.png" -f $n))
    & magick @argv
}

# Hold the finished screen so the loop stays readable.
$final = Join-Path $WorkDir ("frame_{0:D3}.png" -f $lines.Count)
for ($hold = 1; $hold -le 12; $hold++) {
    Copy-Item $final (Join-Path $WorkDir ("frame_{0:D3}.png" -f ($lines.Count + $hold)))
}

& magick -delay 22 -loop 0 (Join-Path $WorkDir "frame_*.png") -layers Optimize -colors 64 $OutputGif
Remove-Item -Recurse -Force $WorkDir -ErrorAction SilentlyContinue

$kb = [math]::Round((Get-Item $OutputGif).Length / 1KB, 1)
Write-Output "Rendered $($lines.Count) lines into $OutputGif (${kb} KB)"
