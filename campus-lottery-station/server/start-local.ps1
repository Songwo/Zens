$ErrorActionPreference = "Stop"

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..\..")

function Get-DotEnvValue {
  param(
    [Parameter(Mandatory = $true)][string]$Name
  )

  $envFiles = @(".env.local", ".env.dev", ".env.development")
  foreach ($envFile in $envFiles) {
    $path = Join-Path $repoRoot $envFile
    if (!(Test-Path $path)) {
      continue
    }
    foreach ($line in Get-Content $path) {
      if ($line -match "^\s*$([regex]::Escape($Name))\s*=\s*(.+?)\s*$") {
        return $Matches[1].Trim().Trim('"').Trim("'")
      }
    }
  }
  return $null
}

$env:LOTTERY_ADDR = ":8093"
$env:LOTTERY_PUBLIC_URL = "http://localhost:8093"
$env:LOTTERY_LOGO_URL = "http://localhost:5173/logo.png"
$env:LOTTERY_DATA = "./data/state.json"
$env:LOTTERY_SESSION_SECRET = "campus-lottery-dev-secret"

$env:COMMUNITY_BASE_URL = "http://localhost:5173"
$env:COMMUNITY_API_BASE_URL = "http://localhost:7800"
$env:COMMUNITY_SSO_AUTHORIZE_URL = "http://localhost:5173/sso/authorize"
$env:COMMUNITY_JWT_SECRET = $env:JWT_SECRET
if ([string]::IsNullOrWhiteSpace($env:COMMUNITY_JWT_SECRET)) {
  $env:COMMUNITY_JWT_SECRET = Get-DotEnvValue -Name "JWT_SECRET"
}
if ([string]::IsNullOrWhiteSpace($env:COMMUNITY_JWT_SECRET)) {
  $env:COMMUNITY_JWT_SECRET = "dev-only-placeholder-secret-override-with-JWT_SECRET-env-in-production"
}

$env:LOTTERY_BOT_USERNAME = "zens-lottery-bot"
$env:LOTTERY_BOT_PASSWORD = "ZensLotteryBot@2026"

# 内部 s2s 回流密钥:必须与主站 internal.service.lottery-secret 一致。
# 这里的默认值对齐主站 application.yml 的 dev 默认,开箱即通;生产用环境变量覆盖。
$env:LOTTERY_SERVICE_ID = "campus-lottery-station"
if ([string]::IsNullOrWhiteSpace($env:LOTTERY_SERVICE_SECRET)) {
  $env:LOTTERY_SERVICE_SECRET = Get-DotEnvValue -Name "LOTTERY_SERVICE_SECRET"
}
if ([string]::IsNullOrWhiteSpace($env:LOTTERY_SERVICE_SECRET)) {
  $env:LOTTERY_SERVICE_SECRET = "dev-lottery-service-secret-CHANGE_ME_at_least_16_chars"
}

.\campus-lottery-station.exe
