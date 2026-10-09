# Publishes a HearthTube release on GitHub (theSiegs/HearthTube, release tag "latest").
# TVs running HearthTube find it through their update check; Downloader can install from the APK links.
#
# Usage:  .\release.ps1 -Notes "What changed"
#         .\release.ps1 -DryRun     build and write the files, but publish nothing
#         .\release.ps1 -Notes "..." -Trailer "Assisted-by: AI"   adds a trailer to the release commit
# Needs:  GitHub CLI (winget install GitHub.cli), signed in once with: gh auth login
param(
    [string]$Notes = "HearthTube update",
    [string]$Trailer = "",
    [switch]$DryRun
)

$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot

$repo = 'theSiegs/HearthTube'
$branch = 'master'
$base = "https://github.com/$repo/releases/download/latest"
$abis = 'armeabi-v7a', 'arm64-v8a', 'x86'
$numberFile = 'smarttubetv\src\hearthtube\release_number.txt'

if (-not $DryRun) {
    if (-not (Get-Command gh -ErrorAction SilentlyContinue)) {
        throw "GitHub CLI not found. Install it with: winget install GitHub.cli   then sign in with: gh auth login"
    }
    if (git status --porcelain) {
        throw "Commit your changes first, so the release matches what's on GitHub."
    }
}

# Each release must have a higher version code than the one before, or TVs won't offer it
$number = [int](Get-Content $numberFile) + 1
if (-not $DryRun) { Set-Content $numberFile $number -Encoding ascii }

$env:JAVA_HOME = "$env:USERPROFILE\dev\tools\jdk-17"
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
& .\gradlew.bat :smarttubetv:assembleHearthtubeDebug
if ($LASTEXITCODE -ne 0) {
    if (-not $DryRun) { git checkout -- $numberFile }
    exit $LASTEXITCODE
}

# Same formula as the hearthtube flavor in smarttubetv\build.gradle
$gradle = Get-Content smarttubetv\build.gradle -Raw
$baseCode = [int]([regex]::Match($gradle, 'versionCode (\d+)').Groups[1].Value)
$baseName = [regex]::Match($gradle, 'versionName "([^"]+)"').Groups[1].Value
$releaseNumber = [int](Get-Content $numberFile)
$versionCode = $baseCode * 1000 + $releaseNumber
$versionName = "$baseName+$releaseNumber"

# Fixed file names, so the links used by Downloader and the update feed never change
$dist = Join-Path $env:TEMP 'hearthtube-release'
Remove-Item $dist -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory $dist | Out-Null
$apkDir = 'smarttubetv\build\outputs\apk\hearthtube\debug'
foreach ($abi in $abis + 'universal') {
    $apk = Get-ChildItem "$apkDir\*_$abi.apk" | Select-Object -First 1
    Copy-Item $apk.FullName "$dist\hearthtube_$abi.apk"
}

# Update feed read by the app (format: SharedModules\appupdatechecker2 AppVersionChecker)
$package = [ordered]@{ downloadUrlList = @("$base/hearthtube_universal.apk") }
foreach ($abi in $abis) { $package["downloadUrlList_$abi"] = @("$base/hearthtube_$abi.apk") }
$feed = [ordered]@{ package = $package }
$feed[$versionName] = [ordered]@{ versionCode = $versionCode; changelog = @($Notes) }
# No BOM: the app's JSON parser rejects it
[System.IO.File]::WriteAllText("$dist\hearthtube.json", ($feed | ConvertTo-Json -Depth 5), (New-Object System.Text.UTF8Encoding $false))

Write-Host "HearthTube $versionName (versionCode $versionCode), files in $dist"

if ($DryRun) {
    Write-Host "Dry run: nothing published."
    exit 0
}

# Record the release number in git first, so the release points at the exact code it was built from
git add $numberFile
if ($Trailer) {
    git commit -m "HearthTube release $versionName" -m $Trailer
} else {
    git commit -m "HearthTube release $versionName"
}
git push

# Replace the previous "latest" release (tools print to stderr when there's none yet; that's fine)
$ErrorActionPreference = 'Continue'
gh release delete latest --repo $repo --yes --cleanup-tag 2>&1 | Out-Null
$ErrorActionPreference = 'Stop'

gh release create latest (Get-ChildItem $dist).FullName --repo $repo --target $branch `
    --title "HearthTube $versionName" --notes $Notes
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Host ""
Write-Host "Published. Downloader link (most TVs): $base/hearthtube_arm64-v8a.apk"
