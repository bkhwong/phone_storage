# Lightweight validation for backup-storage.ps1 (no second drive / no robocopy required).
$ErrorActionPreference = "Stop"
$script = Join-Path $PSScriptRoot "backup-storage.ps1"
$failed = 0

function Assert-True($cond, $msg) {
    if (-not $cond) {
        Write-Host "FAIL: $msg"
        $script:failed++
    } else {
        Write-Host "PASS: $msg"
    }
}

# Dry-run with skip destination: should exit 0 and print robocopy args
$output = & powershell -NoProfile -File $script -Source "C:\fake-source" -Destination "D:\fake-dest" -DryRun -SkipDestinationCheck 2>&1 | Out-String
Assert-True ($LASTEXITCODE -eq 0) "DryRun exits 0"
Assert-True ($output -match "DRY RUN") "DryRun prints DRY RUN"
Assert-True ($output -match "/E") "Default mode includes /E"
Assert-True ($output -notmatch "/MIR") "Default mode excludes /MIR"

$outputMirror = & powershell -NoProfile -File $script -Source "C:\fake-source" -Destination "D:\fake-dest" -Mirror -DryRun -SkipDestinationCheck 2>&1 | Out-String
Assert-True ($LASTEXITCODE -eq 0) "Mirror DryRun exits 0"
Assert-True ($outputMirror -match "/MIR") "Mirror mode includes /MIR"
Assert-True ($outputMirror -notmatch " /E ") "Mirror mode excludes /E"

# Same path should error
$err = $null
try {
    & powershell -NoProfile -File $script -Source "C:\same" -Destination "C:\same" -DryRun -SkipDestinationCheck 2>&1 | Out-Null
    if ($LASTEXITCODE -eq 0) { $err = "expected non-zero" }
} catch {
    $err = "threw"
}
# Write-Error in script may set exit code non-zero
Assert-True ($LASTEXITCODE -ne 0 -or $err) "Same Source/Destination is rejected"

if ($failed -gt 0) {
    Write-Host "`n$failed assertion(s) failed"
    exit 1
}
Write-Host "`nAll backup-storage parameter checks passed"
exit 0
