$ErrorActionPreference = "Stop"

$rootDir = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$backendDir = Join-Path $rootDir "backend"
$frontendDir = Join-Path $rootDir "frontend"
$logDir = Join-Path $rootDir ".codex-logs"
$tunnelScript = Join-Path $rootDir "deploy\db\start-db-tunnel.ps1"

New-Item -ItemType Directory -Force -Path $logDir | Out-Null

function Test-UrlReady {
  param([string]$Url)
  try {
    $response = Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec 3
    return $response.StatusCode -ge 200 -and $response.StatusCode -lt 400
  } catch {
    return $false
  }
}

function Wait-UrlReady {
  param(
    [string]$Url,
    [int]$TimeoutSeconds
  )
  $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
  while ((Get-Date) -lt $deadline) {
    if (Test-UrlReady -Url $Url) {
      return $true
    }
    Start-Sleep -Seconds 1
  }
  return $false
}

function Get-ListeningProcess {
  param([int]$Port)
  $connection = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue |
    Select-Object -First 1
  if (-not $connection) {
    return $null
  }
  return Get-Process -Id $connection.OwningProcess -ErrorAction SilentlyContinue
}

function Get-TunnelMonitorProcess {
  Get-CimInstance Win32_Process |
    Where-Object {
      ($_.Name -eq "powershell.exe" -or $_.Name -eq "pwsh.exe") -and
      $_.CommandLine -like "*monitor-db-tunnel.ps1*" -and
      $_.CommandLine -like "*-LocalPort 13306*"
    } |
    Select-Object -First 1
}

function Test-BackendBuildStale {
  param([string]$JarPath)
  if (-not (Test-Path $JarPath)) {
    return $true
  }
  $jarTime = (Get-Item $JarPath).LastWriteTimeUtc
  $sourceRoots = @(
    (Join-Path $backendDir "pom.xml"),
    (Join-Path $backendDir "src")
  )
  foreach ($path in $sourceRoots) {
    if (-not (Test-Path $path)) {
      continue
    }
    $newer = Get-ChildItem -Path $path -Recurse -File |
      Where-Object { $_.LastWriteTimeUtc -gt $jarTime } |
      Select-Object -First 1
    if ($newer) {
      return $true
    }
  }
  return $false
}

function Ensure-DbTunnel {
  if (-not (Test-Path $tunnelScript)) {
    Write-Host "Tunnel helper missing, skip DB tunnel bootstrap."
    return
  }
  & powershell -ExecutionPolicy Bypass -File $tunnelScript
  if ($LASTEXITCODE -ne 0) {
    throw "DB tunnel bootstrap failed."
  }
  $monitor = Get-TunnelMonitorProcess
  if ($monitor) {
    Write-Host "DB tunnel self-heal monitor active: PID $($monitor.ProcessId)"
  }
}

function Ensure-Backend {
  $healthUrl = "http://127.0.0.1:8080/api/health"
  $readyUrl = "http://127.0.0.1:8080/api/wxpusher/status"
  if (Test-UrlReady -Url $healthUrl) {
    if (Wait-UrlReady -Url $readyUrl -TimeoutSeconds 20) {
      Write-Host "Backend already running: $readyUrl"
      return
    }
  }

  $process = Get-ListeningProcess -Port 8080
  if ($process) {
    throw "Port 8080 is already used by $($process.ProcessName), but the DB-backed backend API is not ready."
  }

  $jarPath = Join-Path $backendDir "target\market-opinion-tracker-0.1.0.jar"
  if (Test-BackendBuildStale -JarPath $jarPath) {
    Push-Location $backendDir
    try {
      & mvn -DskipTests package
      if ($LASTEXITCODE -ne 0) {
        throw "Backend package failed; jar was not created."
      }
    } finally {
      Pop-Location
    }
  }

  Start-Process -FilePath "java" `
    -ArgumentList "-jar", "target\market-opinion-tracker-0.1.0.jar" `
    -WorkingDirectory $backendDir `
    -WindowStyle Hidden `
    -RedirectStandardOutput (Join-Path $logDir "backend.out.log") `
    -RedirectStandardError (Join-Path $logDir "backend.err.log") | Out-Null

  if (-not (Wait-UrlReady -Url $healthUrl -TimeoutSeconds 60)) {
    throw "Backend health start timed out. See .codex-logs\\backend.err.log"
  }
  if (-not (Wait-UrlReady -Url $readyUrl -TimeoutSeconds 60)) {
    throw "Backend DB-backed API start timed out. See .codex-logs\\backend.err.log"
  }
  Write-Host "Backend ready: $readyUrl"
}

function Ensure-Frontend {
  $frontendUrl = "http://127.0.0.1:5173/"
  if (Test-UrlReady -Url $frontendUrl) {
    Write-Host "Frontend already running: $frontendUrl"
    return
  }

  $process = Get-ListeningProcess -Port 5173
  if ($process) {
    throw "Port 5173 is already used by $($process.ProcessName), but the frontend page did not respond."
  }

  Start-Process -FilePath "cmd.exe" `
    -ArgumentList "/c", "npm.cmd run dev -- --host 127.0.0.1" `
    -WorkingDirectory $frontendDir `
    -WindowStyle Hidden `
    -RedirectStandardOutput (Join-Path $logDir "frontend.out.log") `
    -RedirectStandardError (Join-Path $logDir "frontend.err.log") | Out-Null

  if (-not (Wait-UrlReady -Url $frontendUrl -TimeoutSeconds 30)) {
    throw "Frontend start timed out. See .codex-logs\\frontend.err.log"
  }
  Write-Host "Frontend ready: $frontendUrl"
}

Ensure-DbTunnel
Ensure-Backend
Ensure-Frontend

Write-Host "Project ready: http://127.0.0.1:5173/  |  http://127.0.0.1:8080/api/wxpusher/status"
