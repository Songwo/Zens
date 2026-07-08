$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

if (Test-Path ".env.local") {
  Get-Content ".env.local" | ForEach-Object {
    if ($_ -match '^\s*#' -or $_ -match '^\s*$') { return }
    $parts = $_ -split '=', 2
    if ($parts.Length -eq 2) {
      [System.Environment]::SetEnvironmentVariable($parts[0], $parts[1])
    }
  }
}

$port = if ($env:AGENT_SERVICE_PORT) { $env:AGENT_SERVICE_PORT } else { "7810" }
python -m uvicorn app.main:app --host 0.0.0.0 --port $port --reload
