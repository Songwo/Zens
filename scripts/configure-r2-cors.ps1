param(
    [string]$Bucket = $env:R2_BUCKET,
    [string]$Token = $(if ($env:CLOUDFLARE_API_TOKEN) { $env:CLOUDFLARE_API_TOKEN } else { $env:CF_API_TOKEN }),
    [string[]]$Origins = @(
        "https://www.allinsong.top",
        "https://allinsong.top",
        "https://shop.allinsong.top",
        "http://localhost:5173",
        "http://127.0.0.1:5173"
    )
)

$ErrorActionPreference = "Stop"
$ProgressPreference = "SilentlyContinue"

if (-not $Bucket) {
    throw "R2_BUCKET is required. Example: `$env:R2_BUCKET='campus-pulse-media'"
}

if (-not $Token) {
    throw "CF_API_TOKEN or CLOUDFLARE_API_TOKEN is required. Use an Account-scoped token with R2 bucket edit permission."
}

$Root = Resolve-Path (Join-Path $PSScriptRoot "..")
$ArtifactDir = Join-Path $Root ".codex-artifacts\r2"
$null = New-Item -ItemType Directory -Force -Path $ArtifactDir
$CorsPath = Join-Path $ArtifactDir "r2-cors.json"

$cors = [ordered]@{
    rules = @(
        [ordered]@{
            allowed = [ordered]@{
                origins = $Origins
                methods = @("GET", "HEAD", "PUT", "POST")
                headers = @("*")
            }
            exposeHeaders = @("ETag", "Content-Length", "Content-Range", "Accept-Ranges")
            maxAgeSeconds = 86400
        }
    )
}

$cors | ConvertTo-Json -Depth 8 | Set-Content -Path $CorsPath -Encoding UTF8

$oldToken = $env:CLOUDFLARE_API_TOKEN
try {
    $env:CLOUDFLARE_API_TOKEN = $Token
    npx --yes wrangler r2 bucket cors set $Bucket --file $CorsPath
    npx --yes wrangler r2 bucket cors list $Bucket
} finally {
    $env:CLOUDFLARE_API_TOKEN = $oldToken
}

Write-Host "R2 CORS configured for bucket '$Bucket'."
Write-Host "CORS file: $CorsPath"
