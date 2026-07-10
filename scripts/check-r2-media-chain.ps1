param(
    [string]$MediaBaseUrl = "https://media.allinsong.top",
    [string]$AppBaseUrl = "https://www.allinsong.top"
)

$ErrorActionPreference = "Stop"
$ProgressPreference = "SilentlyContinue"

function Test-Head {
    param([string]$Name, [string]$Url)
    try {
        $resp = Invoke-WebRequest -Method Head -Uri $Url -TimeoutSec 15 -UseBasicParsing
        [pscustomobject]@{
            name = $Name
            ok = $true
            status = $resp.StatusCode
            cacheControl = $resp.Headers["Cache-Control"]
            accessControlAllowOrigin = $resp.Headers["Access-Control-Allow-Origin"]
            server = $resp.Headers["Server"]
        }
    } catch {
        [pscustomobject]@{
            name = $Name
            ok = $false
            status = if ($_.Exception.Response) { [int]$_.Exception.Response.StatusCode } else { 0 }
            error = $_.Exception.Message
        }
    }
}

function Test-Options {
    param([string]$Name, [string]$Url)
    try {
        $headers = @{
            Origin = $AppBaseUrl
            "Access-Control-Request-Method" = "PUT"
            "Access-Control-Request-Headers" = "content-type,cache-control"
        }
        $resp = Invoke-WebRequest -Method Options -Uri $Url -Headers $headers -TimeoutSec 15 -UseBasicParsing
        [pscustomobject]@{
            name = $Name
            ok = $true
            status = $resp.StatusCode
            allowOrigin = $resp.Headers["Access-Control-Allow-Origin"]
            allowMethods = $resp.Headers["Access-Control-Allow-Methods"]
            allowHeaders = $resp.Headers["Access-Control-Allow-Headers"]
            exposeHeaders = $resp.Headers["Access-Control-Expose-Headers"]
        }
    } catch {
        [pscustomobject]@{
            name = $Name
            ok = $false
            status = if ($_.Exception.Response) { [int]$_.Exception.Response.StatusCode } else { 0 }
            error = $_.Exception.Message
        }
    }
}

$checks = @(
    (Test-Head -Name "media-domain" -Url $MediaBaseUrl),
    (Test-Options -Name "media-cors-preflight" -Url "$MediaBaseUrl/__cors_probe__")
)

$checks | ConvertTo-Json -Depth 5
