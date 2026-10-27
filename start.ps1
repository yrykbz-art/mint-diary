param([switch]$NoBuild)
$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot

if (!(Test-Path -LiteralPath '.env')) {
    function New-LocalPassword {
        $bytes = New-Object byte[] 24
        $random = [System.Security.Cryptography.RandomNumberGenerator]::Create()
        try { $random.GetBytes($bytes) } finally { $random.Dispose() }
        return ([BitConverter]::ToString($bytes)).Replace('-', '').ToLowerInvariant()
    }
    $databasePassword = New-LocalPassword
    $administratorPassword = New-LocalPassword
    $configuration = @(
        'APP_PORT=5173'
        'MYSQL_DATABASE=mint_diary'
        'MYSQL_USER=mint_diary'
        ('MYSQL_PASSWORD=' + $databasePassword)
        ('MYSQL_ROOT_PASSWORD=' + $administratorPassword)
        'APP_TIME_ZONE=Asia/Singapore'
    )
    [System.IO.File]::WriteAllLines((Join-Path $PSScriptRoot '.env'), $configuration, [System.Text.Encoding]::ASCII)
    Write-Output 'Created local database configuration.'
}

docker compose config --quiet
if ($LASTEXITCODE -ne 0) { throw 'Docker Compose configuration is invalid.' }
if ($NoBuild) { docker compose up -d } else { docker compose up -d --build }
if ($LASTEXITCODE -ne 0) { throw 'Unable to start containers. Check Docker Desktop and the build output.' }

$portLine = Get-Content -LiteralPath '.env' | Where-Object { $_ -match '^APP_PORT=' } | Select-Object -First 1
$applicationPort = if ($portLine) { ($portLine -split '=', 2)[1] } else { '5173' }
$applicationUrl = 'http://localhost:' + $applicationPort
$ready = $false
for ($attempt = 0; $attempt -lt 60; $attempt++) {
    try {
        $health = Invoke-RestMethod -Uri ($applicationUrl + '/api/health') -TimeoutSec 3
        if ($health.status -eq 'ok') { $ready = $true; break }
    } catch { Start-Sleep -Seconds 2 }
}
if (!$ready) { throw 'The application is still starting. Inspect: docker compose logs backend db' }
Write-Output ('Ready: ' + $applicationUrl)
Write-Output 'Stop with: .\stop.ps1   (database and images are preserved)'
