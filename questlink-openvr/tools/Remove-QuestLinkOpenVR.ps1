$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$steamCandidates = @(
  (Join-Path ${env:ProgramFiles(x86)} 'Steam\steamapps\common\SteamVR'),
  (Join-Path $env:ProgramFiles 'Steam\steamapps\common\SteamVR')
)
$steamVr = $steamCandidates | Where-Object { Test-Path (Join-Path $_ 'bin\win64\vrpathreg.exe') } | Select-Object -First 1
if (-not $steamVr) { throw 'SteamVR was not found.' }
& (Join-Path $steamVr 'bin\win64\vrpathreg.exe') removedriver $root
Write-Host 'QuestLink OpenVR driver removed.' -ForegroundColor Green
Read-Host 'Press Enter to close'