# 质量探针：编译 core/ + 全部 tests/*Probe.java + ContentAudit，跑出可复现 CSV。
# 质量探针是"可复现对比"，不是人类胜率主张；断言只在 CoreTests 里。
# 用法： .\scripts\test-quality.ps1 [-Seeds 8] [-SkipPressure]
param(
    [int]$Seeds = 8,
    [switch]$SkipPressure
)
$ErrorActionPreference = 'Stop'
Set-Location (Split-Path $PSScriptRoot -Parent)

$out = 'build/core-quality'
New-Item -ItemType Directory -Force $out, 'build/qa-v3' | Out-Null
Get-ChildItem $out -Recurse -Filter '*.class' -ErrorAction SilentlyContinue | Remove-Item -Force

$tests = @('tests/BalanceProbe.java', 'tests/PressureProbe.java',
    'tests/BranchProbe.java', 'tests/BuildProbe.java', 'tests/ContentAudit.java')
foreach ($t in $tests) {
    if (-not (Test-Path $t)) { throw "缺少探针源文件：$t" }
}

$sources = @(Get-ChildItem core/src -Recurse -Filter '*.java' | ForEach-Object FullName) `
    + @($tests | ForEach-Object { (Resolve-Path $_).Path })

Write-Host "编译 $($sources.Count) 个源文件 → $out"
& javac -encoding UTF-8 -Xlint:-options -d $out $sources
if ($LASTEXITCODE -ne 0) { throw 'Quality probe compile failed' }

# 内容审计：数据表 + 平衡预算 + 契约软性偏差（有失败即中止）
& java -cp $out ContentAudit | Set-Content -Encoding UTF8 build/qa-v3/content-audit.txt
if ($LASTEXITCODE -ne 0) { throw 'ContentAudit failed（见 build/qa-v3/content-audit.txt）' }

# 分支经济
& java -cp $out BranchProbe | Set-Content -Encoding UTF8 build/qa-v3/branch-economy.csv
if ($LASTEXITCODE -ne 0) { throw 'Branch probe failed' }

# 构建交互
& java -cp $out BuildProbe | Set-Content -Encoding UTF8 build/qa-v3/build-interactions.csv
if ($LASTEXITCODE -ne 0) { throw 'Build probe failed' }

if (-not $SkipPressure) {
    & java -cp $out PressureProbe $Seeds | Set-Content -Encoding UTF8 build/qa-v3/pressure-final.csv
    if ($LASTEXITCODE -ne 0) { throw 'Pressure probe failed' }
}

Write-Host '原始结果：build/qa-v3/*.csv（启发式 agent，不是人类胜率）'
