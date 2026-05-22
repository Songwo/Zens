$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $PSScriptRoot
Set-Location $projectRoot

$env:MEDIA_SERVER_PORT = "18090"
$stdout = ".\tmp\smoke.out.log"
$stderr = ".\tmp\smoke.err.log"

$proc = Start-Process -FilePath go -ArgumentList "run", "./cmd/media-service" -PassThru -RedirectStandardOutput $stdout -RedirectStandardError $stderr -WorkingDirectory $projectRoot
try {
  Start-Sleep -Seconds 8
  Invoke-WebRequest "http://127.0.0.1:18090/health" -UseBasicParsing | Select-Object -ExpandProperty Content
} finally {
  if ($proc -and -not $proc.HasExited) {
    Stop-Process -Id $proc.Id -Force
  }
}
