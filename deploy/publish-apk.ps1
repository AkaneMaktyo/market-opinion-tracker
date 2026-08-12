param(
    [string]$SshHost = "103.236.98.149",
    [int]$SshPort = 29453,
    [string]$SshUser = "root",
    [string]$SshPassword = "",
    [string]$VersionName = "1.3",
    [int]$VersionCode = 4,
    [string]$JpushAppKey = "REPLACE_WITH_APPKEY",
    [string]$PublicBaseUrl = "http://103.236.98.149:8888/market",
    [switch]$SkipBuild
)

$ErrorActionPreference = "Stop"

$rootDir = Split-Path -Parent $PSScriptRoot
$frontendDir = Join-Path $rootDir "frontend"
$androidDir = Join-Path $frontendDir "android"
$releaseApk = Join-Path $androidDir "app\build\outputs\apk\release\app-release.apk"
$remoteApkDir = "/var/www/market-opinion-tracker/market/apk"
$remoteApk = "$remoteApkDir/market-opinion-tracker.apk"
$remoteJson = "$remoteApkDir/apk.json"
$publishClient = Join-Path $PSScriptRoot "apk-upload.py"
$localJson = Join-Path $env:TEMP "mot-apk-$PID.json"

if ([string]::IsNullOrWhiteSpace($SshPassword)) {
    throw "Missing -SshPassword."
}
if ([string]::IsNullOrWhiteSpace($JpushAppKey) -or $JpushAppKey -eq "REPLACE_WITH_APPKEY") {
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
if ($missingSigningFiles) {
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

    $jdk = Get-ChildItem 'C:\Program Files\Microsoft' -Directory -Filter 'jdk-21*' |
        Sort-Object LastWriteTime -Descending | Select-Object -First 1
    if (-not $jdk) {
        throw "JDK 21 not found."
    }
    $env:JAVA_HOME = $jdk.FullName
    $env:ANDROID_HOME = Join-Path $env:LOCALAPPDATA 'Android\Sdk'
    $versionCodeArg = "-PversionCode=$VersionCode"
    $versionNameArg = "-PversionName=$VersionName"
    $jpushKeyArg = "-PjpushAppKey=$JpushAppKey"
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
