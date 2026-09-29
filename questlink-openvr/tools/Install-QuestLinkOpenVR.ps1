$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path

$steamCandidates = @(
  (Join-Path ${env:ProgramFiles(x86)} 'Steam\steamapps\common\SteamVR'),
  (Join-Path $env:ProgramFiles 'Steam\steamapps\common\SteamVR')
)

$steamVr = $steamCandidates | Where-Object { Test-Path (Join-Path $_ 'bin\win64\vrpathreg.exe') } | Select-Object -First 1
if (-not $steamVr) { throw 'SteamVR was not found. Install SteamVR first.' }

$vrpathreg = Join-Path $steamVr 'bin\win64\vrpathreg.exe'
& $vrpathreg removedriverswithname questlink 2>$null | Out-Null
& $vrpathreg adddriver $root
if ($LASTEXITCODE -ne 0) { throw "vrpathreg adddriver failed with code $LASTEXITCODE" }

try {
  Get-NetFirewallRule -DisplayName 'QuestLink VR Stream 47991' -ErrorAction SilentlyContinue | Remove-NetFirewallRule -ErrorAction SilentlyContinue
  New-NetFirewallRule -DisplayName 'QuestLink VR Stream 47991' -Direction Inbound -Action Allow -Protocol TCP -LocalPort 47991 -Profile Private | Out-Null
} catch {}

Write-Host ''
Write-Host 'QuestLink OpenVR driver installed.' -ForegroundColor Green
Write-Host "Driver: $root"
Write-Host 'Restart SteamVR before testing Gorilla Tag.' -ForegroundColor Yellow
Read-Host 'Press Enter to close'