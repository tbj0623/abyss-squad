# Codex 逐页验证（带状态确认，不盲点）
$OutputEncoding=[Console]::OutputEncoding=[System.Text.Encoding]::UTF8
$adb="$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
$dev="emulator-5554"; $qa="C:\Users\11930\Desktop\dilao\build\qa8"

function Grab([string]$n){
  & $adb -s $dev shell screencap -p /data/local/tmp/q.png 2>&1|Out-Null
  $p = Join-Path $qa "$n.png"
  & $adb -s $dev pull /data/local/tmp/q.png $p 2>&1|Out-Null
  return $p
}
function Tap([int]$x,[int]$y){ & $adb -s $dev shell input tap $x $y 2>&1|Out-Null; Start-Sleep -Milliseconds 900 }

# 读「第 X / Y 页」区域的平均亮度来判断页码是否变化过难，
# 改为：每次操作后截图，最后统一用 python 比对 sha 判断是否真的翻页。
$TABS=@(155,337,518,700)
$NAMES=@("relics","enemies","bosses","achv")
$PREV_X=360; $NEXT_X=488; $PAGER_Y=757

foreach($t in 0..3){
  Tap $TABS[$t] 133
  Start-Sleep -Milliseconds 700
  $sig = @()
  Grab ("menu-codex-{0}-p0" -f $NAMES[$t]) | Out-Null
  $sig += (Get-FileHash (Join-Path $qa ("menu-codex-{0}-p0.png" -f $NAMES[$t])) -Algorithm MD5).Hash
  $pageCount = 0
  for($p=1;$p -le 6;$p++){
    Tap $NEXT_X $PAGER_Y
    Start-Sleep -Milliseconds 800
    $file = Grab ("menu-codex-{0}-p{1}" -f $NAMES[$t],$p)
    $h = (Get-FileHash $file -Algorithm MD5).Hash
    if($sig -contains $h){
      # 与之前某页相同 => 到末页，回退标记
      Write-Host ("{0}: reached last page after {1} clicks" -f $NAMES[$t],($p-1))
      break
    }
    $sig += $h
    $pageCount = $p
  }
  if($pageCount -eq 0){ Write-Host ("{0}: single page only" -f $NAMES[$t]) }
  # 回到第一页
  for($p=1;$p -le 6;$p++){ Tap $PREV_X $PAGER_Y; Start-Sleep -Milliseconds 250 }
}
Write-Host "DONE"
