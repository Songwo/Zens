$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $PSScriptRoot
Set-Location $projectRoot

New-Item -ItemType Directory -Force -Path ".\logs", ".\tmp", ".\uploads", ".\data" | Out-Null

if (-not $env:MEDIA_ADMIN_PASSWORD) {
  $env:MEDIA_ADMIN_PASSWORD = "admin123456"
}

Write-Host "Starting go-media-service in dev mode..." -ForegroundColor Cyan
go run ./cmd/media-service
