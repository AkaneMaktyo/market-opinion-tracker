$ErrorActionPreference = 'Stop'

function Test-GitHubRoute {
    param([string]$ProxyUrl = '')

    for ($attempt = 1; $attempt -le 4; $attempt++) {
        $arguments = @(
            '--fail', '--silent', '--show-error', '--location',
            '--connect-timeout', '5', '--max-time', '20',
            '--output', 'NUL'
        )
        if ([string]::IsNullOrWhiteSpace($ProxyUrl)) {
            $arguments += @('--noproxy', '*')
        } else {
            $arguments += @('--proxy', $ProxyUrl)
        }
        $arguments += 'https://api.github.com/meta'
        & curl.exe @arguments
        if ($LASTEXITCODE -eq 0) {
            return $true
        }
        Write-Host "GitHub route probe $attempt/4 failed; retrying."
        if ($attempt -lt 4) {
            Start-Sleep -Seconds (2 * $attempt)
        }
    }
    return $false
}

$targets = @('github.com', 'api.github.com', 'codeload.github.com')
foreach ($target in $targets) {
    try {
        $addresses = Resolve-DnsName -Name $target -Type A -ErrorAction Stop |
            Select-Object -ExpandProperty IPAddress -Unique
        Write-Host "DNS $target : $($addresses -join ', ')"
    } catch {
        throw "Runner DNS lookup failed for $target. $($_.Exception.Message)"
    }
}

$directOk = $false
$connection = Test-NetConnection -ComputerName github.com -Port 443 -WarningAction SilentlyContinue
if ($connection.TcpTestSucceeded) {
    $directOk = Test-GitHubRoute
}

$proxyUrl = $env:DEPLOY_HTTPS_PROXY
if ([string]::IsNullOrWhiteSpace($proxyUrl)) {
    $proxyUrl = $env:DEPLOY_HTTP_PROXY
}
if (-not $directOk -and [string]::IsNullOrWhiteSpace($proxyUrl)) {
    $localProxy = Test-NetConnection -ComputerName 127.0.0.1 -Port 7897 -WarningAction SilentlyContinue
    if ($localProxy.TcpTestSucceeded) {
        $proxyUrl = 'http://127.0.0.1:7897'
    }
}
if (-not $directOk -and -not [string]::IsNullOrWhiteSpace($proxyUrl)) {
    if (-not (Test-GitHubRoute -ProxyUrl $proxyUrl)) {
        throw 'Runner proxy request to GitHub failed after 4 attempts.'
    }
}
if (-not $directOk -and [string]::IsNullOrWhiteSpace($proxyUrl)) {
    throw 'Runner cannot reach GitHub and no usable deployment proxy is configured.'
}

$jobProxy = if ($directOk) { '' } else { $proxyUrl }
$jobNoProxy = if ([string]::IsNullOrWhiteSpace($env:DEPLOY_NO_PROXY)) { '' } else { $env:DEPLOY_NO_PROXY }
$environment = "HTTP_PROXY=$jobProxy`nHTTPS_PROXY=$jobProxy`nNO_PROXY=$jobNoProxy`n"
[IO.File]::AppendAllText($env:GITHUB_ENV, $environment, (New-Object System.Text.UTF8Encoding($false)))
Write-Host "GitHub route selected: $(if ($directOk) { 'direct' } else { 'proxy' })."
