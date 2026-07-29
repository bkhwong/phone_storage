<#
.SYNOPSIS
  Copy STORAGE_ROOT to a second drive using robocopy.

.PARAMETER Source
  Source storage root (same as STORAGE_ROOT).

.PARAMETER Destination
  Destination folder on the backup drive.

.PARAMETER Mirror
  If set, use /MIR (deletes files on destination that no longer exist on source).
#>
param(
    [Parameter(Mandatory = $true)]
    [string]$Source,

    [Parameter(Mandatory = $true)]
    [string]$Destination,

    [switch]$Mirror
)

$ErrorActionPreference = "Stop"

if (-not (Test-Path -LiteralPath $Source)) {
    Write-Error "Source does not exist: $Source"
}

New-Item -ItemType Directory -Force -Path $Destination | Out-Null

$robocopyArgs = @(
    $Source,
    $Destination,
    "/E",          # subdirs including empty
    "/R:2",        # retries
    "/W:5",        # wait between retries
    "/FFT",        # assume FAT file times (2s granularity)
    "/Z",          # restartable
    "/XD", ".uploads"  # skip in-progress chunk temps
)

if ($Mirror) {
    # Replace /E with /MIR
    $robocopyArgs = $robocopyArgs | Where-Object { $_ -ne "/E" }
    $robocopyArgs += "/MIR"
    Write-Warning "Mirror mode enabled: files missing on source will be deleted from destination."
}

Write-Host "Backing up:`n  $Source`n-> $Destination"

& robocopy @robocopyArgs
$exit = $LASTEXITCODE

# robocopy exit codes 0-7 are success / partial success
if ($exit -ge 8) {
    Write-Error "robocopy failed with exit code $exit"
} else {
    Write-Host "Backup finished (robocopy exit $exit)."
}
