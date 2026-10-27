$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot
docker compose stop
if ($LASTEXITCODE -ne 0) { throw 'Unable to stop the containers.' }
Write-Output 'Stopped. Your database and pictures are preserved.'
