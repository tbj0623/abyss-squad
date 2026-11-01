# Export a reviewable, credential-free source snapshot for public GitHub publication.
# Never reads or alters the private .git history, keystore, research caches or build directories.
param(
    [Parameter(Mandatory = $true)]
    [string]$Destination
)
$ErrorActionPreference = 'Stop'
$source = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
if (Test-Path -LiteralPath $Destination) {
    throw "Destination already exists; refusing to overwrite: $Destination"
}
New-Item -ItemType Directory -Path $Destination -Force | Out-Null
$destinationRoot = (Resolve-Path -LiteralPath $Destination).Path

$directories = @('core/src', 'app/src', 'gradle/wrapper', 'tests')
foreach ($relative in $directories) {
    $from = Join-Path $source $relative
    $to = Join-Path $destinationRoot $relative
    New-Item -ItemType Directory -Path $to -Force | Out-Null
    Get-ChildItem -LiteralPath $from -Force |
        Where-Object { $relative -ne 'tests' -or $_.Name -notmatch '(?i)^_Dbg\d*\.java$' } |
        Copy-Item -Destination $to -Recurse -Force
}

# Only source scripts are public; never carry over Python caches or fetched research files.
$scriptDest = Join-Path $destinationRoot 'scripts'
New-Item -ItemType Directory -Path $scriptDest -Force | Out-Null
Get-ChildItem -LiteralPath (Join-Path $source 'scripts') -File |
    Where-Object { $_.Extension -in @('.py', '.ps1') } |
    Copy-Item -Destination $scriptDest -Force

$files = @(
    'README.md', 'LICENSE', 'SECURITY.md', 'CONTRIBUTING.md', 'RELEASE_NOTES.md',
    '.gitignore', 'build.gradle', 'settings.gradle', 'gradle.properties',
    'gradlew', 'gradlew.bat', 'app/build.gradle', 'app/proguard-rules.pro',
    'docs/design-v2.md', 'docs/release-verification.md'
)
foreach ($relative in $files) {
    $from = Join-Path $source $relative
    if (-not (Test-Path -LiteralPath $from -PathType Leaf)) {
        throw "Expected source missing: $relative"
    }
    $to = Join-Path $destinationRoot $relative
    New-Item -ItemType Directory -Path (Split-Path $to -Parent) -Force | Out-Null
    Copy-Item -LiteralPath $from -Destination $to -Force
}

$forbidden = Get-ChildItem -LiteralPath $destinationRoot -Recurse -Force |
    Where-Object { $_.Name -match '(?i)^(\.git|\.gradle|__pycache__|local\.properties|keystore\.properties)$|\.(jks|keystore|pem|p12|pfx|env)$' }
if ($forbidden) {
    throw "Blocked sensitive/build paths: $($forbidden.FullName -join ', ')"
}
Write-Host "Public source snapshot prepared: $destinationRoot"
Write-Host "Keystore, Git history, release binaries and scraped research pages excluded."
