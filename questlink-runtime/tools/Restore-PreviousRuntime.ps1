$ErrorActionPreference = 'Stop'

$identity = [Security.Principal.WindowsIdentity]::GetCurrent()
$principal = New-Object Security.Principal.WindowsPrincipal($identity)
if (-not $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
    Start-Process powershell.exe -Verb RunAs -ArgumentList @(
        '-NoProfile',
        '-ExecutionPolicy', 'Bypass',
        '-File', ('"' + $PSCommandPath + '"')
    )
    exit
}

$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$backup = Join-Path $root 'previous_runtime.json'

if (-not (Test-Path $backup)) {
    throw 'No previous_runtime.json backup exists. QuestLink has not saved a previous runtime in this folder.'
}

$state = Get-Content $backup -Raw | ConvertFrom-Json
foreach ($prop in $state.PSObject.Properties) {
    $key = $prop.Name
    $old = $prop.Value
    if (-not (Test-Path $key)) { New-Item -Path $key -Force | Out-Null }

    if ([string]::IsNullOrWhiteSpace([string]$old)) {
        Remove-ItemProperty -Path $key -Name ActiveRuntime -ErrorAction SilentlyContinue
    } else {
        New-ItemProperty -Path $key -Name ActiveRuntime -PropertyType String -Value ([string]$old) -Force | Out-Null
    }
}

Write-Host ''
Write-Host 'Previous OpenXR runtime restored.' -ForegroundColor Green
Read-Host 'Press Enter to close'
