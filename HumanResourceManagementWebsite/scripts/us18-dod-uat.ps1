<#
.SYNOPSIS
  US18 Definition of Done - automated Maven gate + printable UAT matrix (role x intent).

.DESCRIPTION
  - Runs mvnw test (classifier, RBAC AI chat, audit, Gemini off in test profile).
  - Prints manual checklist: compare dataSnapshot to source APIs, unread count (needs running server + JWT).

.PARAMETER SkipTests
  Skip Maven test (print checklist only).

.EXAMPLE
  .\scripts\us18-dod-uat.ps1
  .\scripts\us18-dod-uat.ps1 -SkipTests
#>
param(
    [switch] $SkipTests
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
Set-Location $root

function Write-Result($id, $ok, $detail) {
    $status = if ($ok) { "PASS" } else { "FAIL" }
    Write-Host ("[{0}] {1,-48} {2}" -f $status, $id, $detail)
}

Write-Host ""
Write-Host "=== US18 DoD - automated gate (Maven) ===" -ForegroundColor Cyan
$testsOk = $null
if (-not $SkipTests) {
    & "$root\mvnw.cmd" -q test
    $testsOk = ($LASTEXITCODE -eq 0)
}
else {
    Write-Host '(Skipped Maven tests: you passed -SkipTests)'
}

if ($null -eq $testsOk) {
    Write-Host "[SKIP] DOD-AUTOMATED.mvn-test                           (run without -SkipTests for real gate)"
}
else {
    Write-Result "DOD-AUTOMATED.mvn-test" $testsOk "AiChatIntegrationTest + AiIntentClassifierTest + full suite"
}

Write-Host ""
Write-Host "=== US18 DoD - manual UAT matrix (needs running app + JWT) ===" -ForegroundColor Cyan
Write-Host "Mark each line after checking UI or POST /api/ai/chat."
Write-Host ""

$manual = @(
    'Employee / FAQ policy / 200, FAQ intent, audit row'
    'Employee / self leave (e.g. my leave) / 200, SELF_LEAVE, personal leave snapshot'
    'Employee / self attendance / 200, SELF_ATTENDANCE'
    'Employee / self notifications / 200, SELF_NOTIFICATIONS'
    'Employee / notification summary (tom tat, unread) / 200, NOTIF_SUMMARY'
    'Employee / manager intent (approval queue) / 403, audit FORBIDDEN'
    'Manager HR Admin / approval queue / 200, MGR_PENDING_LEAVE, totalElements vs /api/leave/pending (scope)'
    'Manager HR Admin / team late / 200, MGR_TEAM_ATTENDANCE, vs /api/attendance/history status=LATE (scope)'
    'Any / notifications vs summarize / unread count unchanged after summary prompt'
)

$i = 1
foreach ($line in $manual) {
    Write-Host ("  [{0:D2}] MANUAL  {1}" -f $i, $line)
    $i++
}

Write-Host ""
Write-Host "=== Summary ===" -ForegroundColor Cyan
if ($null -eq $testsOk) {
    Write-Host "Automated gate: SKIPPED (re-run without -SkipTests before merge)." -ForegroundColor Yellow
}
elseif ($testsOk) {
    Write-Host "Automated gate: PASS (mvnw test OK)." -ForegroundColor Green
}
else {
    Write-Host "Automated gate: FAIL - fix tests/build and re-run script." -ForegroundColor Red
    exit 1
}
Write-Host "Manual UAT: confirm each [xx] MANUAL line on staging/production."
Write-Host ""
exit 0
