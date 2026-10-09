# Publishes a HearthTube release on GitHub (theSiegs/HearthTube): a pre-release by default, a stable release with -Stable.
# Each has its own GitHub release and update feed (hearthtube.json):
#   pre-releases     tag "latest"  https://github.com/theSiegs/HearthTube/releases/download/latest/hearthtube.json
#   stable releases  tag "stable"  https://github.com/theSiegs/HearthTube/releases/download/stable/hearthtube.json
# TVs running HearthTube check the stable feed, and the pre-release one too with About > Include pre-releases (copies
# from before that setting check only "latest"); Hearth's updater picks by version from both. Downloader can install
# from the APK links.
#
# Usage:  .\release.ps1 -Notes "What changed"            a pre-release
#         .\release.ps1 -Notes "What changed" -Stable    a stable release
#         .\release.ps1 -DryRun [-Stable]     build, sign and write the files, but publish nothing
#         .\release.ps1 -Notes "..." -Trailer "Assisted-by: AI"   adds a trailer to the release commit
# Needs:  GitHub CLI (winget install GitHub.cli), signed in once with: gh auth login
#         The release key and this PC's Android debug key: see Signing below.
param(
    [string]$Notes = "HearthTube update",
    [string]$Trailer = "",
    [switch]$Stable,
    [switch]$DryRun
)

$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot

$repo = 'theSiegs/HearthTube'
$branch = 'master'
$tag = if ($Stable) { 'stable' } else { 'latest' }
$channel = if ($Stable) { 'release' } else { 'pre-release' }
$base = "https://github.com/$repo/releases/download/$tag"
$abis = 'armeabi-v7a', 'arm64-v8a', 'x86'
$numberFile = 'smarttubetv\src\hearthtube\release_number.txt'

# Signing. HearthTube's first releases were debug builds, signed with this PC's Android debug key (SHA-256 6748528f...).
# Releases are now release builds signed with Hearth's release key (CN=Hearth, O=theSiegs, SHA-256 243bf074...), and
# APK Signature Scheme v3 key rotation lets the copies already installed take them as updates: the lineage file below
# is the proof, signed with the debug key, that Hearth's key replaces it (made once with "apksigner rotate", the debug
# key keeping its installed-data and permission capabilities, no rollback). Android 9 and later check the v3
# signature (Hearth's key plus the lineage); older versions check v1/v2, still made with the debug key.
# EVERY FUTURE RELEASE MUST BE SIGNED THIS WAY, WITH THIS LINEAGE AND BOTH KEYS. Without it, every TV with HearthTube
# refuses the update (INSTALL_FAILED_UPDATE_INCOMPATIBLE) until HearthTube is uninstalled, losing its data. Never
# recreate or replace the lineage; keep it, the debug key and Hearth's key backed up.
# The release key's values: keystore.properties (git-ignored, in this folder) with storeFile, storePassword, keyAlias
# and keyPassword, or with one signingPropertiesFile=<path> line pointing at a file that has them (here: Hearth's
# git-ignored local.properties, so the passwords live in one place); smarttubetv\build.gradle reads them the same way.
# They're never printed: the passwords reach apksigner through environment variables only.
$lineage = 'smarttubetv\src\hearthtube\signing\hearthtube.lineage'
$debugKeystore = "$env:USERPROFILE\.android\debug.keystore"
$rotationMinSdk = 28 # Android 9, the first with v3 signatures

function Read-Properties([string]$path) {
    $props = @{}
    foreach ($line in [System.IO.File]::ReadAllLines($path)) {
        $line = $line.TrimStart()
        if ($line -eq '' -or $line.StartsWith('#') -or $line.StartsWith('!')) { continue }
        $at = $line.IndexOfAny([char[]]'=:')
        if ($at -lt 1) { continue }
        $props[$line.Substring(0, $at).Trim()] = $line.Substring($at + 1).TrimStart()
    }
    return $props
}

# A .properties value as Java (and so Gradle) reads it: \t, \n, \uXXXX, and a backslash before anything else drops
function ConvertFrom-PropertyEscapes([string]$value) {
    return [regex]::Replace($value, '\\(u[0-9a-fA-F]{4}|.)', [System.Text.RegularExpressions.MatchEvaluator] {
        param($match)
        $escaped = $match.Groups[1].Value
        if ($escaped.Length -eq 5) { return [string][char][Convert]::ToInt32($escaped.Substring(1), 16) }
        switch -CaseSensitive ($escaped) { 't' { "`t" } 'n' { "`n" } 'r' { "`r" } 'f' { "`f" } default { $escaped } }
    })
}

function Get-ReleaseKey {
    if (-not (Test-Path 'keystore.properties' -PathType Leaf)) {
        throw "No release key: create keystore.properties here (git-ignored), see Signing in release.ps1."
    }
    $props = Read-Properties (Resolve-Path 'keystore.properties')
    if ($props['signingPropertiesFile']) {
        $pointed = ConvertFrom-PropertyEscapes $props['signingPropertiesFile']
        if (-not (Test-Path $pointed -PathType Leaf)) { throw "keystore.properties: signingPropertiesFile not found." }
        $props = Read-Properties $pointed
    }
    $key = @{}
    foreach ($name in 'storeFile', 'storePassword', 'keyAlias', 'keyPassword') {
        if (-not $props[$name]) { throw "The release key's $name is missing (keystore.properties)." }
        $key[$name] = ConvertFrom-PropertyEscapes $props[$name]
    }
    # Like smarttubetv\build.gradle: relative to smarttubetv, and a Windows path with single backslashes as written
    $store = $key.storeFile
    if (-not [System.IO.Path]::IsPathRooted($store)) { $store = Join-Path "$PSScriptRoot\smarttubetv" $store }
    if (-not (Test-Path $store -PathType Leaf) -and (Test-Path $props['storeFile'] -PathType Leaf)) { $store = $props['storeFile'] }
    if (-not (Test-Path $store -PathType Leaf)) { throw "The release key's storeFile wasn't found." }
    $key.storeFile = $store
    return $key
}

# The SHA-256 digests of the certificates apksigner lists ("<label>... certificate SHA-256 digest: <hex>"), in order
function Get-CertDigests([string[]]$output, [string]$label) {
    $output | Select-String "$label.*certificate SHA-256 digest: ([0-9a-f]{64})" |
        ForEach-Object { $_.Matches[0].Groups[1].Value }
}

if (-not $DryRun) {
    if (-not (Get-Command gh -ErrorAction SilentlyContinue)) {
        throw "GitHub CLI not found. Install it with: winget install GitHub.cli   then sign in with: gh auth login"
    }
    if (git status --porcelain) {
        throw "Commit your changes first, so the release matches what's on GitHub."
    }
}

$env:JAVA_HOME = "$env:USERPROFILE\dev\tools\jdk-17"
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"

# Check the keys before building: apksigner 0.9+ (build-tools 33+) for --rotation-min-sdk-version
$key = Get-ReleaseKey
if (-not (Test-Path $debugKeystore -PathType Leaf)) { throw "This PC's Android debug key wasn't found: $debugKeystore" }
if (-not (Test-Path $lineage -PathType Leaf)) { throw "The key-rotation lineage is missing: $lineage" }
$apksigner = Get-ChildItem "$env:ANDROID_HOME\build-tools\*\apksigner.bat" |
    Sort-Object { try { [version]$_.Directory.Name } catch { [version]'0.0' } } | Select-Object -Last 1
if (-not $apksigner -or [version]$apksigner.Directory.Name -lt [version]'33.0') {
    throw "apksigner from build-tools 33 or later is needed (Android SDK Manager)."
}
$apksigner = $apksigner.FullName
$lineageDigests = @(Get-CertDigests (& $apksigner lineage --in $lineage --print-certs) 'in lineage')
if ($LASTEXITCODE -ne 0 -or $lineageDigests.Count -lt 2) { throw "The key-rotation lineage can't be read: $lineage" }

# Each release must have a higher version code than the one before (on both channels), or TVs won't offer it
$number = [int](Get-Content $numberFile) + 1
if (-not $DryRun) { Set-Content $numberFile $number -Encoding ascii }

& .\gradlew.bat :smarttubetv:assembleHearthtubeRelease
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
$dist = Join-Path $env:TEMP "hearthtube-release-$tag"
Remove-Item $dist -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory $dist | Out-Null
$apkDir = 'smarttubetv\build\outputs\apk\hearthtube\release'

$env:HEARTHTUBE_DEBUG_KEY_PASS = 'android' # the Android SDK's standard debug key password
$env:HEARTHTUBE_STORE_PASS = $key.storePassword
$env:HEARTHTUBE_KEY_PASS = $key.keyPassword
try {
    foreach ($abi in $abis + 'universal') {
        $apk = Join-Path $apkDir "HearthTube_${versionName}_$abi.apk"
        if (-not (Test-Path $apk -PathType Leaf)) { throw "Not built: $apk" }
        $signed = "$dist\hearthtube_$abi.apk"

        # Oldest signer first (v1/v2, Android 8 and older), then Hearth's key with the lineage (v3, Android 9+)
        & $apksigner sign --in $apk --out $signed --v4-signing-enabled false `
            --ks $debugKeystore --ks-key-alias androiddebugkey `
            --ks-pass env:HEARTHTUBE_DEBUG_KEY_PASS --key-pass env:HEARTHTUBE_DEBUG_KEY_PASS `
            --next-signer --ks $key.storeFile --ks-key-alias $key.keyAlias `
            --ks-pass env:HEARTHTUBE_STORE_PASS --key-pass env:HEARTHTUBE_KEY_PASS `
            --lineage $lineage --rotation-min-sdk-version $rotationMinSdk
        if ($LASTEXITCODE -ne 0) { throw "Signing failed: $apk" }

        # Android 9+ must see Hearth's key with the whole lineage (v3); Android 8 and older the debug key (v1/v2)
        $verified = & $apksigner verify -v $signed
        if ($LASTEXITCODE -ne 0) { throw "Doesn't verify: $signed" }
        $schemes = 'v1 scheme (JAR signing): true', 'v2 scheme (APK Signature Scheme v2): true', 'v3 scheme (APK Signature Scheme v3): true'
        foreach ($scheme in $schemes) {
            if (-not ($verified -match [regex]::Escape("Verified using $scheme"))) { throw "Not signed with ${scheme}: $signed" }
        }
        $newAndroid = @(Get-CertDigests (& $apksigner verify --print-certs --min-sdk-version $rotationMinSdk $signed) 'V3')
        $oldAndroid = @(Get-CertDigests (& $apksigner verify --print-certs --max-sdk-version ($rotationMinSdk - 1) $signed) 'V2')
        $apkLineage = @(Get-CertDigests (& $apksigner lineage --in $signed --print-certs) 'in lineage')
        if ($newAndroid.Count -ne 1 -or $newAndroid[0] -ne $lineageDigests[-1] -or
                $oldAndroid.Count -ne 1 -or $oldAndroid[0] -ne $lineageDigests[0] -or
                ($apkLineage -join ',') -ne ($lineageDigests -join ',')) {
            throw "Not signed with Hearth's key, the debug key and the lineage: $signed"
        }
    }
} finally {
    Remove-Item Env:HEARTHTUBE_DEBUG_KEY_PASS, Env:HEARTHTUBE_STORE_PASS, Env:HEARTHTUBE_KEY_PASS -ErrorAction SilentlyContinue
}
Write-Host "Signed with Hearth's key ($($lineageDigests[-1].Substring(0, 8))...), rotated from the debug key ($($lineageDigests[0].Substring(0, 8))...)"

# Update feed read by the app (format: SharedModules\appupdatechecker2 AppVersionChecker), its links on this tag
$package = [ordered]@{ downloadUrlList = @("$base/hearthtube_universal.apk") }
foreach ($abi in $abis) { $package["downloadUrlList_$abi"] = @("$base/hearthtube_$abi.apk") }
$feed = [ordered]@{ package = $package }
$feed[$versionName] = [ordered]@{ versionCode = $versionCode; changelog = @($Notes) }
# No BOM: the app's JSON parser rejects it
[System.IO.File]::WriteAllText("$dist\hearthtube.json", ($feed | ConvertTo-Json -Depth 5), (New-Object System.Text.UTF8Encoding $false))

Write-Host "HearthTube $channel $versionName (versionCode $versionCode, tag $tag), files in $dist"

if ($DryRun) {
    Write-Host "Dry run: nothing published."
    exit 0
}

# Record the release number in git first, so the release points at the exact code it was built from
git add $numberFile
if ($Trailer) {
    git commit -m "HearthTube $channel $versionName" -m $Trailer
} else {
    git commit -m "HearthTube $channel $versionName"
}
git push

# Replace the previous release on this tag (tools print to stderr when there's none yet; that's fine)
$ErrorActionPreference = 'Continue'
gh release delete $tag --repo $repo --yes --cleanup-tag 2>&1 | Out-Null
$ErrorActionPreference = 'Stop'

if ($Stable) {
    gh release create $tag (Get-ChildItem $dist).FullName --repo $repo --target $branch `
        --title "HearthTube $versionName" --notes $Notes --latest
} else {
    gh release create $tag (Get-ChildItem $dist).FullName --repo $repo --target $branch `
        --title "HearthTube $versionName (pre-release)" --notes $Notes --prerelease
}
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Host ""
Write-Host "Published. Downloader link (most TVs): $base/hearthtube_arm64-v8a.apk"
