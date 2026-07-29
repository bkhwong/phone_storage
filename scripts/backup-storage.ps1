<#
.SYNOPSIS
  Copy STORAGE_ROOT to a second drive using robocopy.

.PARAMETER Source
  Source storage root (same as STORAGE_ROOT).

.PARAMETER Destination
  Destination folder on the backup drive.

.PARAMETER Mirror
  If set, use /MIR (deletes files on destination that no longer exist on source).

.PARAMETER DryRun
  Validate parameters and print the robocopy command without executing it.
  Does not require the destination drive to exist when -SkipDestinationCheck is also set.

.PARAMETER SkipDestinationCheck
  With -DryRun, skip creating/checking the destination path (for CI / unit-style checks).
#>
param(
    [Parameter(Mandatory = $true)]
    [string]$Source,

    [Parameter(Mandatory = $true)]
    [string]$Destination,

    [switch]$Mirror,

    [switch]$DryRun,

    [switch]$SkipDestinationCheck
)

$ErrorActionPreference = "Stop"

function Get-RobocopyArgs {
    param(
        [string]$Source,
        [string]$Destination,
        [switch]$Mirror
    )
    $robocopyArgs = @(
        $Source,
        $Destination,
        "/E",
        "/R:2",
        "/W:5",
        "/FFT",
        "/Z",
        "/XD", ".uploads"
    )
    if ($Mirror) {
        $robocopyArgs = @($robocopyArgs | Where-Object { $_ -ne "/E" }) + @("/MIR")
    }
    return ,$robocopyArgs
}

if ([string]::IsNullOrWhiteSpace($Source)) {
    Write-Error "Source must be a non-empty path."
}
if ([string]::IsNullOrWhiteSpace($Destination)) {
    Write-Error "Destination must be a non-empty path."
}
if ($Source -eq $Destination) {
    Write-Error "Source and Destination must be different paths."
}

if (-not $DryRun -or -not $SkipDestinationCheck) {
    if (-not (Test-Path -LiteralPath $Source)) {
        Write-Error "Source does not exist: $Source"
    }
}

$robocopyArgs = Get-RobocopyArgs -Source $Source -Destination $Destination -Mirror:$Mirror

if ($DryRun) {
    Write-Host "DRY RUN: would run robocopy with args:"
    Write-Host ("  robocopy " + ($robocopyArgs -join " "))
    if ($Mirror) {
        Write-Warning "Mirror mode enabled: files missing on source will be deleted from destination."
    }
    exit 0
}

New-Item -ItemType Directory -Force -Path $Destination | Out-Null

Write-Host "Backing up:`n  $Source`n-> $Destination"

& robocopy @robocopyArgs
$exit = $LASTEXITCODE

# robocopy exit codes 0-7 are success / partial success
if ($exit -ge 8) {
    Write-Error "robocopy failed with exit code $exit"
} else {
    Write-Host "Backup finished (robocopy exit $exit)."
}
