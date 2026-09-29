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
$manifest = (Resolve-Path (Join-Path $root 'questlink_runtime.json')).Path
$dll = Join-Path $root 'QuestLinkOpenXRRuntime.dll'
$backup = Join-Path $root 'previous_runtime.json'

if (-not (Test-Path $dll)) {
    throw "QuestLinkOpenXRRuntime.dll is missing from $root"
}

$keys = @(
    'HKLM:\SOFTWARE\Khronos\OpenXR\1',
    'HKLM:\SOFTWARE\WOW6432Node\Khronos\OpenXR\1'
)

$alreadyQuestLink = $true
foreach ($key in $keys) {
    $current = $null
    try { $current = (Get-ItemProperty -Path $key -Name ActiveRuntime -ErrorAction Stop).ActiveRuntime } catch {}
    if ([string]$current -ne $manifest) { $alreadyQuestLink = $false }
}

if (-not $alreadyQuestLink -and -not (Test-Path $backup)) {
    $state = @{}
    foreach ($key in $keys) {
        $old = $null
        try { $old = (Get-ItemProperty -Path $key -Name ActiveRuntime -ErrorAction Stop).ActiveRuntime } catch {}
        $state[$key] = $old
    }
    $state | ConvertTo-Json | Set-Content -Encoding UTF8 $backup
}

foreach ($key in $keys) {
    if (-not (Test-Path $key)) { New-Item -Path $key -Force | Out-Null }
    New-ItemProperty -Path $key -Name ActiveRuntime -PropertyType String -Value $manifest -Force | Out-Null

    $available = Join-Path $key 'AvailableRuntimes'
    if (-not (Test-Path $available)) { New-Item -Path $available -Force | Out-Null }
    New-ItemProperty -Path $available -Name $manifest -PropertyType DWord -Value 0 -Force | Out-Null
}

try {
    $ruleName = 'QuestLink Runtime Stream 47991'
    Get-NetFirewallRule -DisplayName $ruleName -ErrorAction SilentlyContinue | Remove-NetFirewallRule -ErrorAction SilentlyContinue
    New-NetFirewallRule -DisplayName $ruleName -Direction Inbound -Action Allow -Protocol TCP -LocalPort 47991 -Profile Private | Out-Null
    Write-Host 'Firewall: allowed QuestLink stream TCP 47991 on Private networks.' -ForegroundColor Green
} catch {
    Write-Host ('Firewall rule warning: ' + $_.Exception.Message) -ForegroundColor Yellow
}

Write-Host ''
Write-Host 'QuestLink is now the active OpenXR runtime.' -ForegroundColor Green
Write-Host "Manifest: $manifest"
if (Test-Path $backup) {
    Write-Host "Previous runtime saved in: $backup"
}
Write-Host ''
Write-Host 'QuestLink Runtime v0.3 installed. Stream bridge TCP 47991 is enabled for the Quest test client.' -ForegroundColor Yellow
Read-Host 'Press Enter to close'
