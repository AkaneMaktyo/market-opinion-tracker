param(
    [string]$SshHost = "103.236.98.149",
    [int]$SshPort = 29453,
    [string]$SshUser = "root",
    [string]$SshPassword = "",
    [string]$JarPath = "",
    [string]$FrontendArchivePath = "",
    [string]$PublicBaseUrl = "http://103.236.98.149:8888/market",
    [string]$NativeVersionCode = "",
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
$resolvedJarPath = if ($JarPath) { (Resolve-Path $JarPath).Path } else { $defaultJarPath }
$resolvedArchivePath = if ($FrontendArchivePath) { (Resolve-Path $FrontendArchivePath).Path } else { $defaultArchivePath }
$localReleaseScript = Join-Path $env:TEMP "mot-apply-release-$PID.sh"
$localRuntimeEnv = Join-Path $env:TEMP "mot-runtime-env-$PID"
$localMuxScript = Join-Path $PSScriptRoot "ssh_http_mux.py"
$releaseClient = Join-Path $PSScriptRoot "ssh-release.py"
$androidVersionFile = Join-Path $PSScriptRoot "mobile\android-version.json"

if ([string]::IsNullOrWhiteSpace($NativeVersionCode)) {
    if (-not (Test-Path $androidVersionFile)) {
        throw "Android version file not found: $androidVersionFile"
    }
    $androidVersion = Get-Content -Raw $androidVersionFile | ConvertFrom-Json
    $NativeVersionCode = [string]$androidVersion.versionCode
}
if ($NativeVersionCode -notmatch '^\d+$') {
    throw "Invalid Android native version code."
}

function Find-PythonCommand {
    $commands = @()
    $python = Get-Command python -ErrorAction SilentlyContinue
    if ($python) {
        $commands += ,@($python.Source)
    }
    $py = Get-Command py -ErrorAction SilentlyContinue
    if ($py) {
        $commands += ,@($py.Source, "-3")
    }
    $windowsAppsPython = Join-Path ([Environment]::GetFolderPath("LocalApplicationData")) "Microsoft\WindowsApps\python.exe"
    if (Test-Path $windowsAppsPython) {
        $commands += ,@($windowsAppsPython)
    }
    foreach ($command in $commands) {
        try {
            & $command[0] @($command | Select-Object -Skip 1) -c "import paramiko" | Out-Null
            if ($LASTEXITCODE -eq 0) {
                return ,$command
            }
        } catch {
            continue
        }
    }
    throw "Python with paramiko was not found."
}

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
        & cmd /c "npm.cmd run build:update"
        if ($LASTEXITCODE -ne 0) {
            throw "Android live update build failed."
        }
        & node ..\deploy\mobile\create-live-update.mjs `
            --source dist-update `
            --publish dist `
            --base-url $PublicBaseUrl `
            --native-version $NativeVersionCode
        if ($LASTEXITCODE -ne 0) {
            throw "Android live update package failed."
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
if (-not (Test-Path $releaseClient)) {
    throw "Release client not found: $releaseClient"
}

[IO.File]::WriteAllText(
    $localReleaseScript,
    ([IO.File]::ReadAllText((Join-Path $PSScriptRoot "apply-release.sh")) -replace "`r`n", "`n"),
    (New-Object System.Text.UTF8Encoding($false))
)

try {
    $env:MOT_SSH_PASSWORD = $SshPassword
    $pythonCommand = Find-PythonCommand
    $releaseArgs = @(
        "--host", $SshHost,
        "--port", $SshPort,
        "--user", $SshUser,
        "--remote-dir", $remoteDir,
        "--jar", $resolvedJarPath,
        "--archive", $resolvedArchivePath,
        "--script", $localReleaseScript,
        "--mux-script", $localMuxScript
    )
    if (-not [string]::IsNullOrWhiteSpace($env:PRICE_ALERT_WXPUSHER_SPT)) {
        $runtimeSpt = $env:PRICE_ALERT_WXPUSHER_SPT -replace "[`r`n]", ""
        [IO.File]::WriteAllText(
            $localRuntimeEnv,
            "PRICE_ALERT_WXPUSHER_SPT=$runtimeSpt`n",
            (New-Object System.Text.UTF8Encoding($false))
        )
        $releaseArgs += @("--runtime-env", $localRuntimeEnv)
    }
    & $pythonCommand[0] @($pythonCommand | Select-Object -Skip 1) $releaseClient @releaseArgs
    if ($LASTEXITCODE -ne 0) {
        throw "Remote release failed."
    }
    Write-Output "Release complete: http://$($SshHost):8888/market/"
} finally {
    Remove-Item $localReleaseScript -ErrorAction SilentlyContinue
    Remove-Item $localRuntimeEnv -ErrorAction SilentlyContinue
    Remove-Item Env:MOT_SSH_PASSWORD -ErrorAction SilentlyContinue
}
