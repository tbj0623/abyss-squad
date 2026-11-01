param([Parameter(Mandatory=$true)][string]$Serial)
$ErrorActionPreference = 'Stop'
Set-Location (Split-Path $PSScriptRoot -Parent)
if (-not $env:ANDROID_HOME) { $env:ANDROID_HOME = Join-Path $env:LOCALAPPDATA 'Android/Sdk' }
$adb = Join-Path $env:ANDROID_HOME 'platform-tools/adb.exe'
if (-not $Serial.StartsWith('emulator-')) { throw 'Use a disposable emulator. Fixture tests replace app state; do not run on the user phone.' }
& .\gradlew.bat --offline assembleDebug assembleDebugAndroidTest lintDebug
if ($LASTEXITCODE -ne 0) { throw 'Android build failed' }
& $adb -s $Serial install -r app/build/outputs/apk/debug/app-debug.apk
if ($LASTEXITCODE -ne 0) { throw 'App install failed' }
& $adb -s $Serial install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
if ($LASTEXITCODE -ne 0) { throw 'Test install failed' }
New-Item -ItemType Directory -Force build/qa-v3 | Out-Null
$result = & $adb -s $Serial shell am instrument -w -r com.dilao.abysssquad.test/android.test.InstrumentationTestRunner
$result | Set-Content -Encoding UTF8 build/qa-v3/android-tests-final.txt
$result | Write-Output
if (($result -join "`n") -notmatch 'OK \(5 tests\)') { throw 'Instrumentation did not confirm five passing tests' }
& $adb -s $Serial pull /sdcard/Android/data/com.dilao.abysssquad/files/qa-v3 build/qa-v3/rendered-final
