param(
    [string]$SshHost = "103.236.98.149",
    [int]$SshPort = 29453,
    [string]$SshUser = "root",
    [string]$SshPassword = "",
    [string]$JarPath = "",
    [string]$FrontendArchivePath = "",
    [switch]$SkipBuild
)

$ErrorActionPreference = "Stop"

$rootDir = Split-Path -Parent $PSScriptRoot
$backendDir = Join-Path $rootDir "backend"
$frontendDir = Join-Path $rootDir "frontend"
$defaultJarPath = Join-Path $backendDir "target\market-opinion-tracker-0.1.0.jar"
$defaultArchivePath = Join-Path $env:TEMP "market-opinion-frontend-dist.tar.gz"
$remoteDir = "/root/market-opinion-deploy"
$remoteJar = "$remoteDir/market-opinion-tracker-0.1.0.jar"
$remoteArchive = "$remoteDir/frontend-dist.tar.gz"
$remoteScript = "$remoteDir/apply-release.sh"
$remoteMuxScript = "$remoteDir/ssh_http_mux.py"
$remoteTarget = "${SshUser}@${SshHost}:${remoteDir}/"
$resolvedJarPath = if ($JarPath) { (Resolve-Path $JarPath).Path } else { $defaultJarPath }
$resolvedArchivePath = if ($FrontendArchivePath) { (Resolve-Path $FrontendArchivePath).Path } else { $defaultArchivePath }
$localReleaseScript = Join-Path $env:TEMP "mot-apply-release-$PID.sh"
$localMuxScript = Join-Path $PSScriptRoot "ssh_http_mux.py"

if ([string]::IsNullOrWhiteSpace($SshPassword)) {
    throw "Missing -SshPassword."
}

if (-not $SkipBuild) {
    Push-Location $backendDir
    try {
        & mvn -DskipTests package
        if ($LASTEXITCODE -ne 0) {
            throw "Backend package failed."
        }
    } finally {
        Pop-Location
    }

    Push-Location $frontendDir
    try {
        & cmd /c "npm.cmd ci"
        if ($LASTEXITCODE -ne 0) {
            throw "Frontend install failed."
        }
        & cmd /c "set VITE_BASE_PATH=/market/&& npm.cmd run build"
        if ($LASTEXITCODE -ne 0) {
            throw "Frontend build failed."
        }
        & node ..\deploy\validate-frontend-dist.mjs dist
        if ($LASTEXITCODE -ne 0) {
            throw "Frontend validation failed."
        }
        if (Test-Path $resolvedArchivePath) {
            Remove-Item $resolvedArchivePath -Force
        }
        & tar -czf $resolvedArchivePath -C dist .
        if ($LASTEXITCODE -ne 0) {
            throw "Frontend archive failed."
        }
    } finally {
        Pop-Location
    }
}

if (-not (Test-Path $resolvedJarPath)) {
    throw "Jar file not found: $resolvedJarPath"
}
if (-not (Test-Path $resolvedArchivePath)) {
    throw "Frontend archive not found: $resolvedArchivePath"
}
if (-not (Test-Path $localMuxScript)) {
    throw "Mux script not found: $localMuxScript"
}

$askpass = "C:\Windows\Temp\mot-release-askpass-$PID.cmd"
Set-Content -Path $askpass -Value @("@echo off", "echo $SshPassword") -Encoding Ascii
[IO.File]::WriteAllText(
    $localReleaseScript,
    ([IO.File]::ReadAllText((Join-Path $PSScriptRoot "apply-release.sh")) -replace "`r`n", "`n"),
    (New-Object System.Text.UTF8Encoding($false))
)

try {
    $env:SSH_ASKPASS = $askpass
    $env:SSH_ASKPASS_REQUIRE = "force"
    $env:DISPLAY = "codex"
    $scp = (Get-Command scp -ErrorAction Stop).Source
    $ssh = (Get-Command ssh -ErrorAction Stop).Source
    & $ssh -p $SshPort -o PreferredAuthentications=password -o PubkeyAuthentication=no -o NumberOfPasswordPrompts=1 -o StrictHostKeyChecking=accept-new "${SshUser}@${SshHost}" "mkdir -p $remoteDir"
    if ($LASTEXITCODE -ne 0) {
        throw "Remote directory creation failed."
    }
    & $scp -P $SshPort -o PreferredAuthentications=password -o PubkeyAuthentication=no -o NumberOfPasswordPrompts=1 -o StrictHostKeyChecking=accept-new $resolvedJarPath $remoteTarget
    if ($LASTEXITCODE -ne 0) {
        throw "Jar upload failed."
    }
    & $scp -P $SshPort -o PreferredAuthentications=password -o PubkeyAuthentication=no -o NumberOfPasswordPrompts=1 -o StrictHostKeyChecking=accept-new $resolvedArchivePath $remoteTarget
    if ($LASTEXITCODE -ne 0) {
        throw "Frontend upload failed."
    }
    & $scp -P $SshPort -o PreferredAuthentications=password -o PubkeyAuthentication=no -o NumberOfPasswordPrompts=1 -o StrictHostKeyChecking=accept-new $localReleaseScript $remoteScript
    if ($LASTEXITCODE -ne 0) {
        throw "Release script upload failed."
    }
    & $scp -P $SshPort -o PreferredAuthentications=password -o PubkeyAuthentication=no -o NumberOfPasswordPrompts=1 -o StrictHostKeyChecking=accept-new $localMuxScript $remoteMuxScript
    if ($LASTEXITCODE -ne 0) {
        throw "Mux script upload failed."
    }
    & $ssh -p $SshPort -o PreferredAuthentications=password -o PubkeyAuthentication=no -o NumberOfPasswordPrompts=1 -o StrictHostKeyChecking=accept-new "${SshUser}@${SshHost}" "bash $remoteScript $remoteJar $remoteArchive"
    if ($LASTEXITCODE -ne 0) {
        throw "Remote release failed."
    }
    Write-Output "Release complete: http://$($SshHost):8888/market/"
} finally {
    Remove-Item $askpass -ErrorAction SilentlyContinue
    Remove-Item $localReleaseScript -ErrorAction SilentlyContinue
    Remove-Item Env:SSH_ASKPASS, Env:SSH_ASKPASS_REQUIRE, Env:DISPLAY -ErrorAction SilentlyContinue
}
