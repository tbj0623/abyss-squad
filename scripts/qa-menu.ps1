# 深渊小队 — 菜单侧 QA 驱动（可靠版）
# 关键教训：本模拟器每帧约 8-15 秒（主机 CPU 100%，两个模拟器抢核），
# 点击后必须靠"目标像素轮询"确认，不能靠固定 sleep，也不能靠整屏 diff
# （主菜单右侧插画每 5s 自动切换会污染 diff）。
#
# 用法: pwsh -NoProfile -File scripts\qa-menu.ps1 -Action <name>

param([Parameter(Mandatory=$true)][string]$Action)

$ErrorActionPreference = 'Continue'
$OutputEncoding = [Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
$dev = "emulator-5556"
$root = "C:\Users\11930\Desktop\dilao"
$qa = Join-Path $root "build\qa7"
New-Item -ItemType Directory -Path $qa -Force | Out-Null

function Shot([string]$name) {
    & $adb -s $dev shell screencap -p /data/local/tmp/qa.png 2>&1 | Out-Null
    $local = Join-Path $qa "$name.png"
    & $adb -s $dev pull /data/local/tmp/qa.png $local 2>&1 | Out-Null
    return $local
}

function Tap([int]$x, [int]$y) { & $adb -s $dev shell input tap $x $y 2>&1 | Out-Null }

# 屏幕指纹：用几个关键探针像素判断当前是哪一屏
function Fingerprint([string]$file) {
    $py = @"
from PIL import Image
im=Image.open(r'$file').convert('RGB')
def p(x,y):
    return im.getpixel((x,y))
# CharScreen: 顶部 4 段 segmented 在 y=128；卡片网格起点 y=287
# MenuScreen: 大 mint 按钮中心 (334,425) 是亮绿 (144,215,179) 左右
# MapScreen: 右上"离开远征"按钮区 (1334,90) 偏亮
print('MINT', p(334,425))
print('CHAR_TAB', p(80,128))
print('CARD', p(150,287))
print('COVER', p(1000,300))
print('MAPBTN', p(1334,90))
"@
    $t = Join-Path $env:TEMP "fp.py"
    Set-Content -Path $t -Value $py -Encoding UTF8
    return (python $t) -join "`n"
}

function IsMenu([string]$fp) {
    # 主菜单大按钮：绿色通道显著高
    if ($fp -match 'MINT \((\d+), (\d+), (\d+)\)') {
        return ([int]$matches[2] -gt 150 -and [int]$matches[3] -gt 120 -and [int]$matches[1] -lt 190)
    }
    return $false
}

function IsChar([string]$fp) {
    # CharScreen 特征：顶部 4 段 segmented 选中态在 y=128 有一个亮框；
    # 更稳的是右侧详情面板的玻璃底 (1100,150) 或职业卡片选中边框 (68,212 附近)
    # 这里用「左侧第 1 张卡片的选中描边」：CharScreen 默认选中 role0，边框是薄荷绿
    if ($fp -match 'CARD \((\d+), (\d+), (\d+)\)') {
        $r=[int]$matches[1]; $g=[int]$matches[2]; $b=[int]$matches[3]
        # 允许任意非纯黑/非主菜单配色：CharScreen 卡片区是深青灰，明显不是背景黑
        return ($r -gt 25 -and $g -gt 35)
    }
    return $false
}

function WaitScreen([string]$kind, [int]$timeoutSec = 150) {
    $end = (Get-Date).AddSeconds($timeoutSec)
    $i = 0
    while ((Get-Date) -lt $end) {
        Start-Sleep -Seconds 5
        $f = Shot "wait-$kind-$i"
        $fp = Fingerprint $f
        $ok = if ($kind -eq 'menu') { IsMenu $fp } elseif ($kind -eq 'char') { IsChar $fp } else { $false }
        Write-Host "    [$kind] try $i : $(($fp -split "`n")[0]) $(($fp -split "`n")[1]) -> $ok"
        if ($ok) { return $f }
        $i++
    }
    return $null
}

function RestartToMenu {
    & $adb -s $dev shell am force-stop com.dilao.abysssquad 2>&1 | Out-Null
    Start-Sleep -Seconds 3
    & $adb -s $dev shell am start -n com.dilao.abysssquad/.MainActivity 2>&1 | Out-Null
    $f = WaitScreen 'menu' 180
    if ($null -eq $f) { Write-Host "!! 未能回到主菜单"; return $null }
    return $f
}

function GotoChar {
    $m = RestartToMenu
    if ($null -eq $m) { return $null }
    Tap 334 425
    return (WaitScreen 'char' 180)
}

switch ($Action) {

    'menu' {
        $f = RestartToMenu
        if ($f) { Write-Host "OK menu -> $f" }
    }

    'char' {
        $f = GotoChar
        if ($f) { Write-Host "OK char -> $f" }
    }

    'char-modes' {
        $f = GotoChar
        if ($null -eq $f) { break }
        # 依次点 4 个模式 tab，每个都截图保存
        $xs = @(155, 337, 519, 701)
        for ($i = 0; $i -lt 4; $i++) {
            Tap $xs[$i] 128
            Start-Sleep -Seconds 12
            $s = Shot "menu-char-mode$i"
            Write-Host "mode $i -> $s"
        }
    }

    'char-roles' {
        $f = GotoChar
        if ($null -eq $f) { break }
        # 12 个职业逐个点：4 列 × 3 行
        for ($i = 0; $i -lt 12; $i++) {
            $col = $i % 4; $row = [math]::Floor($i / 4)
            $x = 64 + $col * 184 + 86
            $y = 212 + $row * 160 + 75
            Tap $x $y
            Start-Sleep -Seconds 11
            $s = Shot "menu-char-role$i"
            Write-Host "role $i -> $s"
        }
    }

    'char-pacts' {
        $f = GotoChar
        if ($null -eq $f) { break }
        for ($i = 0; $i -lt 5; $i++) {
            $x = 64 + $i * 194 + 97
            Tap $x 748
            Start-Sleep -Seconds 11
            $s = Shot "menu-char-pact$i"
            Write-Host "pact $i -> $s"
        }
    }

    'codex' {
        $m = RestartToMenu; if ($null -eq $m) { break }
        Tap 128 684    # 图鉴
        Start-Sleep -Seconds 20
        $s = Shot "menu-codex"
        Write-Host "codex -> $s"
    }

    'codex-tabs' {
        $m = RestartToMenu; if ($null -eq $m) { break }
        Tap 128 684; Start-Sleep -Seconds 20
        # 4 个 tab: x=64..790, 每个 181.5
        $xs = @(155, 337, 518, 700)
        for ($i = 0; $i -lt 4; $i++) {
            Tap $xs[$i] 133
            Start-Sleep -Seconds 14
            $s = Shot "menu-codex-tab$i"
            Write-Host "codex tab $i -> $s"
        }
    }

    'meta' {
        $m = RestartToMenu; if ($null -eq $m) { break }
        Tap 265 684    # 元进度
        Start-Sleep -Seconds 22
        $s = Shot "menu-meta"
        Write-Host "meta -> $s"
    }

    'settings' {
        $m = RestartToMenu; if ($null -eq $m) { break }
        Tap 403 684    # 设置
        Start-Sleep -Seconds 22
        $s = Shot "menu-settings"
        Write-Host "settings -> $s"
    }

    'state' {
        $s = Shot "state-now"
        $fp = Fingerprint $s
        Write-Host $fp
        Write-Host "-> $s"
    }

    default { Write-Host "unknown action: $Action" }
}
