# UI 冒烟：把本轮修过的界面逐屏截图，供人工核对。
#
# 为什么要有这个脚本：本轮修的 12 个 UX 缺陷里有 9 个是"画出来了但位置错/文字缺"，
# 只有看图才能确认。纯 JVM 测试测不到 Canvas 几何，静态审计也只能查越界不能查重叠。
#
# 用法：
#   .\scripts\qa-smoke.ps1              # 全部
#   .\scripts\qa-smoke.ps1 -Only codex # 只跑某一屏
#
# 前置：模拟器 1440x810（1:1 逻辑分辨率），应用已安装。
# 输出：build/smoke/*.png

param([string]$Only = "")

$ErrorActionPreference = 'Stop'
Set-Location (Split-Path $PSScriptRoot -Parent)
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
$dev = "emulator-5554"
$out = "build/smoke"
New-Item -ItemType Directory -Force $out | Out-Null

# 主机负载高时模拟器会 ANR，截出来的图全是对话框，不如不截。
$load = (Get-CimInstance Win32_Processor | Measure-Object -Property LoadPercentage -Average).Average
if ($load -gt 75) {
    Write-Host "主机 CPU $load%，等待 25 秒（否则模拟器会 ANR，截图无效）"
    Start-Sleep -Seconds 25
}

function Shot([string]$n) {
    $p = Join-Path $out "$n.png"
    & $adb -s $dev exec-out screencap -p > $p
    $sz = (Get-Item $p).Length
    if ($sz -lt 20000) { Write-Warning "$n 只有 $sz 字节 —— 可能是黑屏或 ANR 对话框" }
    Write-Output "  $n  ($([int]($sz/1024)) KB)"
}
function Tap([int]$x, [int]$y) {
    & $adb -s $dev shell input tap $x $y | Out-Null
    Start-Sleep -Milliseconds 1400
}
function Restart() {
    & $adb -s $dev shell am force-stop com.dilao.abysssquad | Out-Null
    Start-Sleep -Milliseconds 800
    & $adb -s $dev shell am start -n com.dilao.abysssquad/.MainActivity | Out-Null
    Start-Sleep -Seconds 8
}
function Want([string]$k) { return ($Only -eq "" -or $Only -eq $k) }

Write-Host "== UI 冒烟 =="
Restart
if (Want "menu")    { Shot "01-menu" }

if (Want "codex") {
    Tap 128 684                       # 底栏「图鉴」
    Shot "02-codex-relics"
    Tap 340 133; Shot "03-codex-enemies"   # 敌种：4 行不能溢出
    Tap 508 133; Shot "04-codex-bosses"
    Tap 690 133; Shot "05-codex-achievements"  # 名称/描述/品质/奖励
    Tap 1366 47                       # 返回
    Start-Sleep -Milliseconds 1200
}

if (Want "settings") {
    Tap 335 590                       # 底栏「设置」
    Shot "06-settings"
    Tap 1366 47
    Start-Sleep -Milliseconds 1200
}

if (Want "char") {
    Tap 336 419                       # 开始远征
    Shot "07-char"                    # 印记不被遮挡 / 契约档位报价
    if (Want "pact") {
        Tap 928 47; Shot "08-pact"    # 契约词条
        Tap 190 738                   # 返回
        Start-Sleep -Milliseconds 1200
        Tap 1092 47; Shot "09-doctrine"  # 天赋牌
        Tap 190 738
        Start-Sleep -Milliseconds 1200
        Tap 928 47
    }
}

Write-Host "完成，见 $out/"
