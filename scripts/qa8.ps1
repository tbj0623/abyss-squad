# QA 驱动（5554, 1440x810, 1:1 坐标）
param([Parameter(Mandatory=$true)][string]$Action, [int]$Arg = 0)

$ErrorActionPreference='Continue'
$OutputEncoding=[Console]::OutputEncoding=[System.Text.Encoding]::UTF8
$adb="$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
$dev="emulator-5554"
$qa="C:\Users\11930\Desktop\dilao\build\qa8"
New-Item -ItemType Directory -Path $qa -Force | Out-Null

function Shot([string]$n){
  & $adb -s $dev shell screencap -p /data/local/tmp/q.png 2>&1|Out-Null
  $p=Join-Path $qa "$n.png"; & $adb -s $dev pull /data/local/tmp/q.png $p 2>&1|Out-Null; return $p
}
function Tap([int]$x,[int]$y){ & $adb -s $dev shell input tap $x $y 2>&1|Out-Null; Start-Sleep -Milliseconds 550 }
function BackMenu{ & $adb -s $dev shell am force-stop com.dilao.abysssquad 2>&1|Out-Null; Start-Sleep -Seconds 2
  & $adb -s $dev shell am start -n com.dilao.abysssquad/.MainActivity 2>&1|Out-Null; Start-Sleep -Seconds 6 }

switch($Action){
  'codex' {
    BackMenu
    Tap 128 684          # 图鉴
    Start-Sleep -Seconds 2
    Shot "menu-codex-tab0"
  }
  'codex-tabs' {
    # 4 tabs at y=133: x=64..790 (w 726/4=181.5)
    $xs=@(145,327,508,690)
    for($i=0;$i -lt 4;$i++){
      Tap $xs[$i] 133
      Start-Sleep -Seconds 1
      Shot "menu-codex-tab$i"
    }
  }
  'codex-pages' {
    # 当前 tab 反复点「下一页」，最多 8 次
    for($i=1;$i -le 8;$i++){
      Tap 1288 752      # 下一页按钮 (x+w-132 .. )
      Start-Sleep -Seconds 1
      Shot "menu-codex-page$i"
    }
  }
  'meta' {
    BackMenu; Tap 265 684; Start-Sleep -Seconds 2; Shot "menu-meta"
  }
  'settings' {
    BackMenu; Tap 403 684; Start-Sleep -Seconds 2; Shot "menu-settings"
  }
  'shot' { Shot "menu-shot$Arg" }
  'tap'  { Tap $Arg 0 }
  default { Write-Host "?" }
}
