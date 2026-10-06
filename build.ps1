# Builds the HearthTube (stplus) debug APK and, if a device or emulator is connected, installs it.
# Usage:  .\build.ps1            build + install
#         .\build.ps1 -NoInstall build only
param([switch]$NoInstall)

$ErrorActionPreference = 'Stop'

# This Gradle version needs JDK 17 (Android Studio's bundled JDK 21 is too new)
$env:JAVA_HOME = "$env:USERPROFILE\dev\tools\jdk-17"
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"

Set-Location $PSScriptRoot

& .\gradlew.bat :smarttubetv:assembleStplusDebug
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

$apk = Get-ChildItem smarttubetv\build\outputs\apk\stplus\debug\*universal*.apk | Select-Object -First 1
Write-Host "APK: $($apk.FullName)"

if (-not $NoInstall) {
    $adb = "$env:ANDROID_HOME\platform-tools\adb.exe"
    $devices = & $adb devices | Select-String "\tdevice$"
    if ($devices) {
        & $adb install -r $apk.FullName
    } else {
        Write-Host "No device connected, skipping install."
    }
}
