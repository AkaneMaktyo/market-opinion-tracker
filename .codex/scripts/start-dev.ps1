$ErrorActionPreference = "Stop"

$rootDir = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$backendDir = Join-Path $rootDir "backend"
$frontendDir = Join-Path $rootDir "frontend"
$logDir = Join-Path $rootDir ".codex-logs"
$tunnelScript = Join-Path $rootDir "deploy\start-db-tunnel.ps1"

New-Item -ItemType Directory -Force -Path $logDir | Out-Null

function Test-UrlReady {
  param([string]$Url)

  try {
    $response = Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec 2
    return $response.StatusCode -ge 200 -and $response.StatusCode -lt 500
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

function Ensure-DbTunnel {
  if (-not (Test-Path $tunnelScript)) {
    Write-Host "Tunnel helper missing, skip DB tunnel bootstrap."
    return
  }
  & powershell -ExecutionPolicy Bypass -File $tunnelScript
  if ($LASTEXITCODE -ne 0) {
    throw "DB tunnel bootstrap failed."
  }
}

function Ensure-Backend {
  $healthUrl = "http://127.0.0.1:8080/api/health"
  if (Test-UrlReady -Url $healthUrl) {
    Write-Host "Backend already running: $healthUrl"
    return
  }

  $process = Get-ListeningProcess -Port 8080
  if ($process) {
    throw "Port 8080 is already used by $($process.ProcessName), but the backend health check failed."
  }

  $jarPath = Join-Path $backendDir "target\market-opinion-tracker-0.1.0.jar"
  if (-not (Test-Path $jarPath)) {
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
    throw "Backend start timed out. See .codex-logs\\backend.err.log"
  }

  Write-Host "Backend ready: $healthUrl"
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

Write-Host "Project ready: http://127.0.0.1:5173/  |  http://127.0.0.1:8080/api/health"
