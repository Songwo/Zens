$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $PSScriptRoot
Set-Location $projectRoot

New-Item -ItemType Directory -Force -Path ".\logs", ".\tmp", ".\uploads", ".\data" | Out-Null

foreach ($name in @("MEDIA_UPLOAD_JWT_SECRET", "MEDIA_ADMIN_PASSWORD", "MEDIA_ADMIN_JWT_SECRET", "MEDIA_ADMIN_SERVICE_TOKENS")) {
  if (-not [Environment]::GetEnvironmentVariable($name)) {
    throw "$name is required; load it from a local, untracked env file first"
  }
}

Write-Host "Starting go-media-service in dev mode..." -ForegroundColor Cyan
go run ./cmd/media-service
