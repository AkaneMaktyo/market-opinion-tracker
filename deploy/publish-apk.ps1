param(
    [string]$SshHost = "103.236.98.149",
    [int]$SshPort = 29453,
    [string]$SshUser = "root",
    [string]$SshPassword = "",
    [string]$VersionName = "",
    [int]$VersionCode = 0,
    [string]$JpushAppKey = "REPLACE_WITH_APPKEY",
    [string]$ApkPath = "",
    [string]$PublicBaseUrl = "http://103.236.98.149:8888/market",
    [switch]$SkipBuild
)

$ErrorActionPreference = "Stop"

$rootDir = Split-Path -Parent $PSScriptRoot
$frontendDir = Join-Path $rootDir "frontend"
$androidDir = Join-Path $frontendDir "android"
$releaseApk = if ([string]::IsNullOrWhiteSpace($ApkPath)) {
    Join-Path $androidDir "app\build\outputs\apk\release\app-release.apk"
} else {
    (Resolve-Path $ApkPath).Path
}
$remoteApkDir = "/var/www/market-opinion-tracker/market/apk"
$remoteApk = "$remoteApkDir/market-opinion-tracker.apk"
$remoteJson = "$remoteApkDir/apk.json"
$publishClient = Join-Path $PSScriptRoot "apk-upload.py"
$localJson = Join-Path $env:TEMP "mot-apk-$PID.json"
$versionFile = Join-Path $PSScriptRoot "mobile\android-version.json"

if ([string]::IsNullOrWhiteSpace($VersionName) -or $VersionCode -le 0) {
    if (-not (Test-Path $versionFile)) {
        throw "Android version file not found: $versionFile"
    }
    $version = Get-Content -Raw $versionFile | ConvertFrom-Json
    if ([string]::IsNullOrWhiteSpace($VersionName)) {
        $VersionName = [string]$version.versionName
    }
    if ($VersionCode -le 0) {
        $VersionCode = [int]$version.versionCode
    }
}
if ([string]::IsNullOrWhiteSpace($VersionName) -or $VersionCode -le 0) {
    throw "Invalid Android version."
}

function Set-GradleProxyEnvironment {
    $proxyUrl = if (-not [string]::IsNullOrWhiteSpace($env:HTTPS_PROXY)) {
        $env:HTTPS_PROXY
    } else {
        $env:HTTP_PROXY
    }
    if ([string]::IsNullOrWhiteSpace($proxyUrl)) {
        return
    }
    $uri = [Uri]$proxyUrl
    if (-not $uri.Host -or $uri.Port -le 0) {
        throw "Invalid HTTP(S) proxy URL for Gradle."
    }
    $options = @(
        "-Dhttp.proxyHost=$($uri.Host)",
        "-Dhttp.proxyPort=$($uri.Port)",
        "-Dhttps.proxyHost=$($uri.Host)",
        "-Dhttps.proxyPort=$($uri.Port)"
    ) -join ' '
    $env:JAVA_TOOL_OPTIONS = "$($env:JAVA_TOOL_OPTIONS) $options".Trim()
}

function Set-AndroidBuildEnvironment {
    $jdk = Get-ChildItem 'C:\Program Files\Microsoft' -Directory -Filter 'jdk-21*' -ErrorAction SilentlyContinue |
        Sort-Object LastWriteTime -Descending | Select-Object -First 1
    if (-not $jdk) {
        throw "JDK 21 not found."
    }
    $env:JAVA_HOME = $jdk.FullName
    $sdkCandidates = @(
        $env:ANDROID_HOME,
        $env:ANDROID_SDK_ROOT,
        (Join-Path $env:LOCALAPPDATA 'Android\Sdk'),
        'C:\Android\Sdk'
    ) | Where-Object { -not [string]::IsNullOrWhiteSpace($_) } | Select-Object -Unique
    $sdk = $sdkCandidates | Where-Object { Test-Path (Join-Path $_ 'platform-tools') } | Select-Object -First 1
    if (-not $sdk) {
        throw "Android SDK not found. Checked: $($sdkCandidates -join ', ')"
    }
    $env:ANDROID_HOME = $sdk
    $env:ANDROID_SDK_ROOT = $sdk
}

if ([string]::IsNullOrWhiteSpace($SshPassword)) {
    throw "Missing -SshPassword."
}
if (-not $SkipBuild -and ([string]::IsNullOrWhiteSpace($JpushAppKey) -or $JpushAppKey -eq "REPLACE_WITH_APPKEY")) {
    throw "Missing production -JpushAppKey."
}
$signingHome = if ([string]::IsNullOrWhiteSpace($env:MOT_ANDROID_SIGNING_HOME)) {
    Join-Path ([Environment]::GetFolderPath('UserProfile')) '.market-opinion-tracker'
} else {
    $env:MOT_ANDROID_SIGNING_HOME
}
$signingFiles = @(
    (Join-Path $signingHome 'android-release.jks'),
    (Join-Path $signingHome 'android-signing.properties')
)
$missingSigningFiles = $signingFiles | Where-Object { -not (Test-Path $_) }
if (-not $SkipBuild -and $missingSigningFiles) {
    throw "Android release signing files are missing: $($missingSigningFiles -join ', ')"
}

if (-not $SkipBuild) {
    Push-Location $frontendDir
    try {
        & cmd /c "npm.cmd run android:sync"
        if ($LASTEXITCODE -ne 0) {
            throw "Android sync failed."
        }
    } finally {
        Pop-Location
    }

    Set-AndroidBuildEnvironment
    $versionCodeArg = "-PversionCode=$VersionCode"
    $versionNameArg = "-PversionName=$VersionName"
    $jpushKeyArg = "-PjpushAppKey=$JpushAppKey"
    Set-GradleProxyEnvironment
    Push-Location $androidDir
    try {
        & .\gradlew.bat ':app:assembleRelease' $versionCodeArg $versionNameArg $jpushKeyArg '--console=plain'
        if ($LASTEXITCODE -ne 0) {
            throw "APK release build failed."
        }
    } finally {
        Pop-Location
    }
}

if (-not (Test-Path $releaseApk)) {
    throw "Release APK not found: $releaseApk"
}
if (-not (Test-Path $publishClient)) {
    throw "Upload client not found: $publishClient"
}

$file = Get-Item $releaseApk
$size = $file.Length
$now = (Get-Date).ToUniversalTime().ToString("yyyy-MM-ddTHH:mm:ssZ")
$json = [ordered]@{
    versionName = $VersionName
    versionCode = $VersionCode
    size = $size
    url = "$PublicBaseUrl/apk/market-opinion-tracker.apk"
    updatedAt = $now
} | ConvertTo-Json
[IO.File]::WriteAllText(
    $localJson,
    $json,
    (New-Object System.Text.UTF8Encoding($false))
)

try {
    $env:MOT_SSH_PASSWORD = $SshPassword
    & python $publishClient `
        --host $SshHost `
        --port $SshPort `
        --user $SshUser `
        --apk $releaseApk `
        --json $localJson `
        --remote-dir $remoteApkDir
    if ($LASTEXITCODE -ne 0) {
        throw "APK upload failed."
    }
    Write-Output "APK published: $PublicBaseUrl/apk/market-opinion-tracker.apk"
} finally {
    Remove-Item $localJson -ErrorAction SilentlyContinue
    Remove-Item Env:MOT_SSH_PASSWORD -ErrorAction SilentlyContinue
}
