param(
    [string]$Name = "shot",
    [int]$Wait = 4
)
# 统一截图脚本：先确保应用在前台，再截图拉回本地
$ErrorActionPreference = 'Stop'
Set-Location (Split-Path $PSScriptRoot -Parent)
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
$dev = "emulator-5554"

# ── 负载前置检查 ──────────────────────────────────────────────────────────
# 实测：主机 CPU 被并发构建/探针吃满时，模拟器会 ANR 甚至 adb 掉线，
# 截出来的图要么是黑屏要么是 ANR 对话框，完全误导人（本轮排查里踩过两次）。
# 所以先在负载高时等一下，而不是直接截一张假图。
$load = (Get-CimInstance Win32_Processor | Measure-Object -Property LoadPercentage -Average).Average
if ($load -gt 75) {
    Write-Output "主机 CPU $load%，等待 20 秒再截图（避免 ANR 假图）"
    Start-Sleep -Seconds 20
}

function Tap([int]$x, [int]$y) {
    & $adb -s $dev shell input tap $x $y | Out-Null
    Start-Sleep -Milliseconds 900
}

function Shot([string]$n) {
    & $adb -s $dev shell screencap -p /sdcard/_s.png | Out-Null
    & $adb -s $dev pull /sdcard/_s.png "build\shots\$n.png" 2>&1 | Out-Null
    Write-Output "saved build\shots\$n.png"
}

function EnsureApp() {
    $f = (& $adb -s $dev shell dumpsys window | Select-String 'mCurrentFocus' | Select-Object -First 1).ToString()
    if ($f -notmatch 'abysssquad') {
        & $adb -s $dev shell am start -n com.dilao.abysssquad/.MainActivity | Out-Null
        Start-Sleep -Seconds 6
    }
}

EnsureApp
Start-Sleep -Seconds $Wait
Shot $Name
