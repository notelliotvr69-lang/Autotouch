@echo off
echo ========================================
echo QuestLink OpenXR Runtime Status
echo ========================================
echo.
powershell.exe -NoProfile -Command "$p='HKLM:\SOFTWARE\Khronos\OpenXR\1'; try {$v=(Get-ItemProperty -Path $p -Name ActiveRuntime -ErrorAction Stop).ActiveRuntime; Write-Host ('ActiveRuntime: ' + $v)} catch {Write-Host 'ActiveRuntime is not set.'}"
echo.
pause
