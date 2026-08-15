param(
    [string]$SshHost = "103.236.98.149",
    [int]$SshPort = 29453,
    [string]$SshUser = "root",
    [string]$SshPassword = "",
    [string]$JarPath = "",
    [string]$FrontendArchivePath = "",
    [string]$BinanceEnvPath = "",
    [ValidateSet("preserve", "paper", "live")]
    [string]$BinanceTradingMode = "preserve",
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
$defaultBinanceEnvPath = Join-Path $backendDir ".env"
$resolvedBinanceEnvPath = if ($BinanceEnvPath) {
    (Resolve-Path $BinanceEnvPath).Path
} else {
    $defaultBinanceEnvPath
}
$resolvedBinancePrivateKey = ""

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

function Read-DotEnvValue {
    param(
        [string]$Path,
        [string]$Name
    )
    foreach ($rawLine in Get-Content -LiteralPath $Path) {
        $line = $rawLine.Trim()
        if (-not $line -or $line.StartsWith("#") -or -not $line.Contains("=")) {
            continue
        }
        $parts = $line.Split("=", 2)
        if ($parts[0].Trim() -ne $Name) {
            continue
        }
        $value = $parts[1].Trim()
        if ($value.Length -ge 2) {
            $first = $value.Substring(0, 1)
            $last = $value.Substring($value.Length - 1, 1)
            if (($first -eq '"' -and $last -eq '"') -or ($first -eq "'" -and $last -eq "'")) {
                $value = $value.Substring(1, $value.Length - 2)
            }
        }
        return $value
    }
    return ""
}

function ConvertTo-SystemdEnvValue {
    param([string]$Value)
    if ($Value -match "[`r`n]") {
        throw "Runtime environment values must be single-line."
    }
    $escaped = $Value.Replace("\", "\\").Replace('"', '\"')
    return '"' + $escaped + '"'
}

function Resolve-BinancePrivateKey {
    param(
        [string]$EnvPath,
        [string]$ConfiguredPath
    )
    $candidate = if ([IO.Path]::IsPathRooted($ConfiguredPath)) {
        $ConfiguredPath
    } else {
        Join-Path (Split-Path -Parent $EnvPath) $ConfiguredPath
    }
    $resolved = (Resolve-Path $candidate).Path
    $header = Get-Content -LiteralPath $resolved -TotalCount 1
    if ($header -notin @("-----BEGIN PRIVATE KEY-----", "-----BEGIN ENCRYPTED PRIVATE KEY-----")) {
        throw "Binance RSA file must be a PKCS#8 private key."
    }
    return $resolved
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

    $runtimeLines = [Collections.Generic.List[string]]::new()
    if (-not [string]::IsNullOrWhiteSpace($env:PRICE_ALERT_WXPUSHER_SPT)) {
        $runtimeSpt = $env:PRICE_ALERT_WXPUSHER_SPT -replace "[`r`n]", ""
        $runtimeLines.Add("PRICE_ALERT_WXPUSHER_SPT=$(ConvertTo-SystemdEnvValue $runtimeSpt)")
    }

    if (Test-Path $resolvedBinanceEnvPath) {
        $binanceApiKey = Read-DotEnvValue $resolvedBinanceEnvPath "BINANCE_SPOT_API_KEY"
        $binancePrivateKeyPath = Read-DotEnvValue $resolvedBinanceEnvPath "BINANCE_SPOT_PRIVATE_KEY_PATH"
        if ($binanceApiKey -or $binancePrivateKeyPath) {
            $binanceKeyType = Read-DotEnvValue $resolvedBinanceEnvPath "BINANCE_SPOT_KEY_TYPE"
            if ($binanceKeyType.ToUpperInvariant() -ne "RSA") {
                throw "Binance deployment requires BINANCE_SPOT_KEY_TYPE=RSA."
            }
            if (-not $binanceApiKey -or -not $binancePrivateKeyPath) {
                throw "Binance API key or RSA private key path is missing."
            }
            $resolvedBinancePrivateKey = Resolve-BinancePrivateKey `
                $resolvedBinanceEnvPath $binancePrivateKeyPath
            $binanceEnvironment = Read-DotEnvValue `
                $resolvedBinanceEnvPath "BINANCE_SPOT_ENVIRONMENT"
            if (-not $binanceEnvironment) {
                $binanceEnvironment = "testnet"
            }
            if ($binanceEnvironment -notin @("mainnet", "testnet")) {
                throw "Unsupported Binance environment."
            }
            $runtimeLines.Add("BINANCE_SPOT_API_KEY=$(ConvertTo-SystemdEnvValue $binanceApiKey)")
            $runtimeLines.Add('BINANCE_SPOT_KEY_TYPE="RSA"')
            if ($BinanceTradingMode -eq "paper") {
                $runtimeLines.Add('BINANCE_SPOT_ENABLED="false"')
                $runtimeLines.Add('BINANCE_SPOT_PAPER_TRADING="true"')
            } elseif ($BinanceTradingMode -eq "live") {
                $runtimeLines.Add('BINANCE_SPOT_ENABLED="true"')
                $runtimeLines.Add('BINANCE_SPOT_PAPER_TRADING="false"')
            }
            $runtimeLines.Add("BINANCE_SPOT_ENVIRONMENT=$(ConvertTo-SystemdEnvValue $binanceEnvironment)")
            $binanceProxyUrl = Read-DotEnvValue $resolvedBinanceEnvPath "BINANCE_SPOT_PROXY_URL"
            if (-not $binanceProxyUrl) {
                $binanceProxyUrl = "http://127.0.0.1:7897"
            }
            $runtimeLines.Add("BINANCE_SPOT_PROXY_URL=$(ConvertTo-SystemdEnvValue $binanceProxyUrl)")
            $runtimeLines.Add('BINANCE_SPOT_PRIVATE_KEY_PATH="/etc/market-opinion-tracker/keys/binance-spot-private.pem"')
            $binancePassphrase = Read-DotEnvValue `
                $resolvedBinanceEnvPath "BINANCE_SPOT_PRIVATE_KEY_PASSPHRASE"
            if ($binancePassphrase) {
                $runtimeLines.Add("BINANCE_SPOT_PRIVATE_KEY_PASSPHRASE=$(ConvertTo-SystemdEnvValue $binancePassphrase)")
            }
            $releaseArgs += @("--binance-private-key", $resolvedBinancePrivateKey)
        }
    }

    if ($runtimeLines.Count -gt 0) {
        [IO.File]::WriteAllText(
            $localRuntimeEnv,
            (($runtimeLines -join "`n") + "`n"),
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
