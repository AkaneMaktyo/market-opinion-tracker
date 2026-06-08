param(
    [int]$LocalPort = 13306,
    [string]$RemoteDbHost = "127.0.0.1",
    [int]$RemoteDbPort = 3306,
    [string]$SshHost = "",
    [int]$SshPort = 29453,
    [string]$SshUser = "",
    [string]$SshPassword = "",
    [switch]$ForceRestart
)

$ErrorActionPreference = "Stop"
$root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$envFile = Join-Path $root "backend\.env"

function Get-EnvMap {
    param([string]$Path)
    $result = @{}
    if (-not (Test-Path $Path)) { return $result }
    foreach ($line in Get-Content $Path) {
        if ([string]::IsNullOrWhiteSpace($line) -or $line.TrimStart().StartsWith("#")) { continue }
        $parts = $line.Split("=", 2)
        if ($parts.Count -eq 2) { $result[$parts[0].Trim()] = $parts[1].Trim() }
    }
    return $result
}

function Get-TunnelProcesses {
    param([int]$Port)
    $pattern = "-L $Port`:$RemoteDbHost`:$RemoteDbPort"
    Get-CimInstance Win32_Process -Filter "Name='ssh.exe'" |
        Where-Object { $_.CommandLine -like "*$pattern*" }
}

function Test-Port {
    param([int]$Port)
    try {
        return (Test-NetConnection 127.0.0.1 -Port $Port -WarningAction SilentlyContinue).TcpTestSucceeded
    } catch {
        return $false
    }
}

function Stop-TunnelProcesses {
    param([object[]]$Processes)
    foreach ($process in $Processes) {
        Stop-Process -Id $process.ProcessId -Force -ErrorAction SilentlyContinue
    }
    Start-Sleep -Seconds 1
}

$envMap = Get-EnvMap -Path $envFile
if (-not $SshHost) { $SshHost = $env:KMT_SSH_HOST }
if (-not $SshUser) { $SshUser = $env:KMT_SSH_USER }
if (-not $SshPassword) { $SshPassword = $env:KMT_SSH_PASSWORD }
if (-not $SshHost) { $SshHost = "103.236.98.149" }
if (-not $SshUser) { $SshUser = "root" }
if ([string]::IsNullOrWhiteSpace($SshPassword)) {
    throw "Missing SSH password. Pass -SshPassword or set KMT_SSH_PASSWORD first."
}

$existing = @(Get-TunnelProcesses -Port $LocalPort)
if (-not $ForceRestart -and $existing.Count -gt 0 -and (Test-Port -Port $LocalPort)) {
    Write-Output "MySQL tunnel already listening on 127.0.0.1:${LocalPort}"
    $existing | Select-Object ProcessId, CommandLine
    exit 0
}
if ($existing.Count -gt 0) {
    Stop-TunnelProcesses -Processes $existing
}

$logDir = Join-Path $root ".codex-logs"
if (-not (Test-Path $logDir)) {
    New-Item -ItemType Directory -Force -Path $logDir | Out-Null
}
$askpass = "C:\Windows\Temp\mot-ssh-askpass-$PID.cmd"
$stdoutLog = Join-Path $logDir "mysql-tunnel.out.log"
$stderrLog = Join-Path $logDir "mysql-tunnel.err.log"
Set-Content -Path $askpass -Value @("@echo off", "echo $SshPassword") -Encoding Ascii

try {
    $env:SSH_ASKPASS = $askpass
    $env:SSH_ASKPASS_REQUIRE = "force"
    $env:DISPLAY = "codex"
    $ssh = (Get-Command ssh -ErrorAction Stop).Source
    $args = @(
        "-N",
        "-L", "$LocalPort`:$RemoteDbHost`:$RemoteDbPort",
        "-p", "$SshPort",
        "-o", "ExitOnForwardFailure=yes",
        "-o", "ServerAliveInterval=30",
        "-o", "ServerAliveCountMax=3",
        "-o", "StrictHostKeyChecking=accept-new",
        "$SshUser@$SshHost"
    )
    $process = Start-Process -FilePath $ssh -ArgumentList $args -WorkingDirectory $root -RedirectStandardOutput $stdoutLog -RedirectStandardError $stderrLog -WindowStyle Hidden -PassThru
    $healthy = $false
    for ($i = 0; $i -lt 12; $i++) {
        Start-Sleep -Seconds 1
        if ($process.HasExited) { break }
        if (Test-Port -Port $LocalPort) {
            $healthy = $true
            break
        }
    }
    if (-not $healthy) {
        if (-not $process.HasExited) {
            Stop-Process -Id $process.Id -Force -ErrorAction SilentlyContinue
        }
        $tail = if (Test-Path $stderrLog) { (Get-Content $stderrLog -Tail 40) -join [Environment]::NewLine } else { "" }
        throw "MySQL tunnel failed on 127.0.0.1:${LocalPort}`n$tail"
    }
    if ($envMap.ContainsKey("SPRING_DATASOURCE_URL")) {
        Write-Output "Tunnel ready for $($envMap['SPRING_DATASOURCE_URL'])"
    } else {
        Write-Output "MySQL tunnel ready on 127.0.0.1:${LocalPort}"
    }
    Get-CimInstance Win32_Process -Filter "ProcessId=$($process.Id)" | Select-Object ProcessId, Name, CommandLine
} finally {
    Remove-Item $askpass -ErrorAction SilentlyContinue
    Remove-Item Env:SSH_ASKPASS, Env:SSH_ASKPASS_REQUIRE, Env:DISPLAY -ErrorAction SilentlyContinue
}
