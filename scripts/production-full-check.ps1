param(
    [switch]$Repair,
    [string]$BaseUrl = "https://www.allinsong.top",
    [int]$TimeoutSec = 20
)

$ErrorActionPreference = "Stop"
$ProgressPreference = "SilentlyContinue"

Add-Type -AssemblyName System.Net.Http

$Root = Resolve-Path (Join-Path $PSScriptRoot "..")
$LogDir = Join-Path $Root ".codex-artifacts\production-checks"
$null = New-Item -ItemType Directory -Force -Path $LogDir

$StartedAt = Get-Date
$Stamp = $StartedAt.ToString("yyyyMMdd-HHmmss")
$TranscriptPath = Join-Path $LogDir "$Stamp.log"
$ReportPath = Join-Path $LogDir "$Stamp.json"

Start-Transcript -Path $TranscriptPath -Append | Out-Null

$Checks = New-Object System.Collections.Generic.List[object]

function Add-Check {
    param(
        [string]$Name,
        $Ok,
        [string]$Detail,
        [int]$StatusCode = 0,
        [long]$DurationMs = 0
    )
    $isOk = [System.Convert]::ToBoolean($Ok)
    $Checks.Add([pscustomobject]@{
        name       = $Name
        ok         = $isOk
        statusCode = $StatusCode
        durationMs = $DurationMs
        detail     = $Detail
    })
    $mark = if ($isOk) { "OK" } else { "FAIL" }
    Write-Host "[$mark] $Name $Detail"
}

function Invoke-HttpCheck {
    param(
        [string]$Name,
        [string]$Method,
        [string]$Path,
        [object]$Body = $null,
        [scriptblock]$Validate = $null,
        [int[]]$ExpectedStatusCodes = @()
    )

    $uri = if ($Path.StartsWith("http")) { $Path } else { "$BaseUrl$Path" }
    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    try {
        $headers = @{
            "Accept" = "application/json,text/html;q=0.9,*/*;q=0.8"
            "User-Agent" = "CodexProductionFullCheck/1.0"
        }
        $args = @{
            Uri = $uri
            Method = $Method
            TimeoutSec = $TimeoutSec
            Headers = $headers
            UseBasicParsing = $true
        }
        if ($null -ne $Body) {
            $args.ContentType = "application/json"
            $args.Body = ($Body | ConvertTo-Json -Compress -Depth 8)
        }
        $resp = Invoke-WebRequest @args
        $sw.Stop()

        $ok = if ($ExpectedStatusCodes.Count -gt 0) {
            $ExpectedStatusCodes -contains [int]$resp.StatusCode
        } else {
            $resp.StatusCode -ge 200 -and $resp.StatusCode -lt 300
        }
        $detail = "HTTP $($resp.StatusCode)"
        if ($Validate) {
            $validated = & $Validate $resp
            $ok = $ok -and [bool]$validated.ok
            if ($validated.detail) {
                $detail = "$detail; $($validated.detail)"
            }
        }
        Add-Check -Name $Name -Ok $ok -Detail $detail -StatusCode $resp.StatusCode -DurationMs $sw.ElapsedMilliseconds
    } catch {
        $sw.Stop()
        $status = 0
        if ($_.Exception.Response -and $_.Exception.Response.StatusCode) {
            $status = [int]$_.Exception.Response.StatusCode
        }
        $detail = $_.Exception.Message
        if ($_.ScriptStackTrace) {
            $detail = "$detail; $($_.ScriptStackTrace)"
        }
        if ($ExpectedStatusCodes.Count -gt 0 -and $ExpectedStatusCodes -contains $status) {
            Add-Check -Name $Name -Ok $true -Detail "HTTP $status expected" -StatusCode $status -DurationMs $sw.ElapsedMilliseconds
            return
        }
        Add-Check -Name $Name -Ok $false -Detail $detail -StatusCode $status -DurationMs $sw.ElapsedMilliseconds
    }
}

function Invoke-CommandCheck {
    param(
        [string]$Name,
        [string]$Command,
        [string]$WorkingDirectory
    )
    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    try {
        Push-Location $WorkingDirectory
        $oldErrorActionPreference = $ErrorActionPreference
        try {
            $ErrorActionPreference = "Continue"
            $global:LASTEXITCODE = 0
            $output = & cmd.exe /d /s /c $Command 2>&1 | Out-String
            $code = [int]$LASTEXITCODE
        } finally {
            $ErrorActionPreference = $oldErrorActionPreference
            Pop-Location
        }
        $sw.Stop()
        $ok = ($code -eq 0)
        $tail = ($output -split "`r?`n" | Where-Object { $_.Trim() } | Select-Object -Last 3) -join " | "
        if (-not $tail) { $tail = "exit=$code" }
        $tail = "exit=$code ok=$ok; $tail"
        Add-Check -Name $Name -Ok $ok -Detail $tail -StatusCode $code -DurationMs $sw.ElapsedMilliseconds
    } catch {
        $sw.Stop()
        Add-Check -Name $Name -Ok $false -Detail $_.Exception.Message -DurationMs $sw.ElapsedMilliseconds
    }
}

function Invoke-R2CorsPreflightCheck {
    param(
        [string]$Name = "r2:media-cors-preflight",
        [string]$MediaBaseUrl = "https://media.allinsong.top",
        [string]$Origin = $BaseUrl
    )
    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    try {
        $headers = @{
            Origin = $Origin
            "Access-Control-Request-Method" = "PUT"
            "Access-Control-Request-Headers" = "content-type,cache-control"
            "User-Agent" = "CodexProductionFullCheck/1.0"
        }
        $resp = Invoke-WebRequest `
            -Method Options `
            -Uri "$MediaBaseUrl/__cors_probe__" `
            -Headers $headers `
            -TimeoutSec $TimeoutSec `
            -UseBasicParsing
        $sw.Stop()
        $allowOrigin = [string]$resp.Headers["Access-Control-Allow-Origin"]
        $allowMethods = [string]$resp.Headers["Access-Control-Allow-Methods"]
        $exposeHeaders = [string]$resp.Headers["Access-Control-Expose-Headers"]
        $ok = $resp.StatusCode -ge 200 -and $resp.StatusCode -lt 300 `
            -and $allowOrigin `
            -and $allowMethods.ToUpperInvariant().Contains("PUT")
        Add-Check -Name $Name -Ok $ok `
            -Detail "HTTP $($resp.StatusCode); origin=$Origin; allowOrigin=$allowOrigin; allowMethods=$allowMethods; exposeHeaders=$exposeHeaders" `
            -StatusCode $resp.StatusCode `
            -DurationMs $sw.ElapsedMilliseconds
    } catch {
        $sw.Stop()
        $status = 0
        if ($_.Exception.Response -and $_.Exception.Response.StatusCode) {
            $status = [int]$_.Exception.Response.StatusCode
        }
        Add-Check -Name $Name -Ok $false -Detail $_.Exception.Message -StatusCode $status -DurationMs $sw.ElapsedMilliseconds
    }
}

function Invoke-RedirectCheck {
    param(
        [string]$Name,
        [string]$Uri,
        [string]$ExpectedLocation
    )
    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    $handler = [System.Net.Http.HttpClientHandler]::new()
    $handler.AllowAutoRedirect = $false
    $client = [System.Net.Http.HttpClient]::new($handler)
    $client.Timeout = [TimeSpan]::FromSeconds($TimeoutSec)
    try {
        $request = [System.Net.Http.HttpRequestMessage]::new([System.Net.Http.HttpMethod]::Get, $Uri)
        $request.Headers.UserAgent.ParseAdd("CodexProductionFullCheck/1.0")
        $resp = $client.SendAsync($request).GetAwaiter().GetResult()
        $sw.Stop()
        $location = if ($resp.Headers.Location) { $resp.Headers.Location.ToString() } else { "" }
        $ok = [int]$resp.StatusCode -eq 308 -and $location -eq $ExpectedLocation
        Add-Check -Name $Name -Ok $ok -Detail "HTTP $([int]$resp.StatusCode); location=$location" `
            -StatusCode ([int]$resp.StatusCode) -DurationMs $sw.ElapsedMilliseconds
    } catch {
        $sw.Stop()
        Add-Check -Name $Name -Ok $false -Detail $_.Exception.Message -DurationMs $sw.ElapsedMilliseconds
    } finally {
        $client.Dispose()
        $handler.Dispose()
    }
}

Write-Host "Campus Pulse production full check started at $StartedAt"
Write-Host "BaseUrl=$BaseUrl Repair=$Repair Root=$Root"

$jsonOk = {
    param($resp)
    try {
        $data = $resp.Content | ConvertFrom-Json
        [pscustomobject]@{
            ok = ($data.code -eq 2000)
            detail = "code=$($data.code); message=$($data.message)"
        }
    } catch {
        [pscustomobject]@{ ok = $false; detail = "invalid json: $($_.Exception.Message)" }
    }
}

$actuatorHealthOk = {
    param($resp)
    try {
        # Actuator 的 vendor JSON content-type 在 Windows PowerShell 中可能返回 byte[]。
        $raw = if ($resp.Content -is [byte[]]) {
            [Text.Encoding]::UTF8.GetString($resp.Content)
        } else {
            [string]$resp.Content
        }
        $data = $raw | ConvertFrom-Json
        [pscustomobject]@{
            ok = ([string]$data.status -eq "UP")
            detail = "status=$($data.status)"
        }
    } catch {
        [pscustomobject]@{ ok = $false; detail = "invalid actuator json: $($_.Exception.Message)" }
    }
}

$script:SamplePostId = $null
$script:SampleUserId = $null
$script:SampleSectionId = $null
$documentsOk = {
    param($resp)
    try {
        $data = $resp.Content | ConvertFrom-Json
        $records = @($data.data.records)
        if ($records.Count -gt 0) {
            $script:SamplePostId = [string]$records[0].id
            $script:SampleUserId = [string]$records[0].userId
        }
        [pscustomobject]@{
            ok = ($data.code -eq 2000 -and $records.Count -gt 0)
            detail = "code=$($data.code); records=$($records.Count); samplePost=$script:SamplePostId"
        }
    } catch {
        [pscustomobject]@{ ok = $false; detail = "invalid json: $($_.Exception.Message)" }
    }
}

$sectionsOk = {
    param($resp)
    try {
        $data = $resp.Content | ConvertFrom-Json
        $records = @($data.data)
        if ($records.Count -gt 0) {
            $script:SampleSectionId = [string]$records[0].id
        }
        [pscustomobject]@{
            ok = ($data.code -eq 2000 -and $records.Count -gt 0)
            detail = "code=$($data.code); sections=$($records.Count); sampleSection=$script:SampleSectionId"
        }
    } catch {
        [pscustomobject]@{ ok = $false; detail = "invalid json: $($_.Exception.Message)" }
    }
}

$canonicalCommunityConfig = {
    param($resp)
    try {
        $payload = $resp.Content | ConvertFrom-Json
        $communityUrl = if ($payload.communityUrl) {
            [string]$payload.communityUrl
        } elseif ($payload.data -and $payload.data.communityUrl) {
            [string]$payload.data.communityUrl
        } else {
            ""
        }
        $expected = $BaseUrl.TrimEnd("/")
        $actual = $communityUrl.TrimEnd("/")
        [pscustomobject]@{
            ok = ($actual -eq $expected)
            detail = "communityUrl=$actual; expected=$expected"
        }
    } catch {
        [pscustomobject]@{ ok = $false; detail = "invalid json: $($_.Exception.Message)" }
    }
}

$canonicalSsoClient = {
    param($resp)
    try {
        $payload = $resp.Content | ConvertFrom-Json
        $logoUrl = [string]$payload.data.logoUrl
        $expectedPrefix = $BaseUrl.TrimEnd("/") + "/"
        [pscustomobject]@{
            ok = ($payload.code -eq 2000 -and $logoUrl.StartsWith($expectedPrefix))
            detail = "code=$($payload.code); logoUrl=$logoUrl; expectedPrefix=$expectedPrefix"
        }
    } catch {
        [pscustomobject]@{ ok = $false; detail = "invalid json: $($_.Exception.Message)" }
    }
}

Invoke-HttpCheck -Name "frontend:index" -Method GET -Path "/" -Validate {
    param($resp)
    [pscustomobject]@{
        ok = ($resp.Content -match "<script" -and $resp.Content -match "/assets/")
        detail = "htmlBytes=$($resp.Content.Length)"
    }
}
Invoke-HttpCheck -Name "api:actuator-health" -Method GET -Path "/api/actuator/health" -Validate $actuatorHealthOk
Invoke-HttpCheck -Name "api:home-bootstrap" -Method GET -Path "/api/public/home-bootstrap?pageSize=5" -Validate $jsonOk
Invoke-HttpCheck -Name "api:sections" -Method GET -Path "/api/section/active" -Validate $sectionsOk
Invoke-HttpCheck -Name "api:hot-tags" -Method GET -Path "/api/tag/hot?limit=5" -Validate $jsonOk
Invoke-HttpCheck -Name "api:documents-list" -Method POST -Path "/api/documents/list" -Body @{ pageNum = 1; pageSize = 10 } -Validate $documentsOk
Invoke-HttpCheck -Name "api:post-search-lists" -Method POST -Path "/api/post/search-lists" -Body @{ pageNum = 1; pageSize = 10 } -Validate $jsonOk
Invoke-HttpCheck -Name "api:section-list" -Method GET -Path "/api/section/list" -Validate $jsonOk
Invoke-HttpCheck -Name "api:level-thresholds" -Method GET -Path "/api/level/thresholds" -Validate $jsonOk
Invoke-HttpCheck -Name "api:trust-thresholds" -Method GET -Path "/api/trust-level/thresholds" -Validate $jsonOk
Invoke-HttpCheck -Name "api:stats-site" -Method GET -Path "/api/stats/site" -Validate $jsonOk
Invoke-HttpCheck -Name "api:trend-dashboard" -Method GET -Path "/api/trend-stat/dashboard" -Validate $jsonOk
Invoke-HttpCheck -Name "api:trend-post" -Method GET -Path "/api/trend-stat/post-trend" -Validate $jsonOk
Invoke-HttpCheck -Name "api:trend-user" -Method GET -Path "/api/trend-stat/user-trend" -Validate $jsonOk
Invoke-HttpCheck -Name "api:trend-section-pie" -Method GET -Path "/api/trend-stat/section-pie" -Validate $jsonOk
Invoke-HttpCheck -Name "api:heat-rank" -Method GET -Path "/api/heat-rank/top?timeRange=WEEK&limit=10" -Validate $jsonOk
Invoke-HttpCheck -Name "api:recommend-list" -Method GET -Path "/api/recommend/list?page=1&pageSize=10" -Validate $jsonOk
Invoke-HttpCheck -Name "api:tag-search" -Method GET -Path "/api/tag/search?keyword=Java" -Validate $jsonOk
Invoke-HttpCheck -Name "api:search-hot-keywords" -Method GET -Path "/api/search/hot-keywords" -Validate $jsonOk
Invoke-HttpCheck -Name "api:search-suggestions" -Method GET -Path "/api/search/suggestions?keyword=Java" -Validate $jsonOk
Invoke-HttpCheck -Name "api:invite-required" -Method GET -Path "/api/invite/required" -Validate $jsonOk
Invoke-HttpCheck -Name "api:changelog-list" -Method GET -Path "/api/changelog/list" -Validate $jsonOk
Invoke-HttpCheck -Name "api:agent-health" -Method GET -Path "/api/agent/health" -Validate $jsonOk
Invoke-HttpCheck -Name "api:sso-public-shop" -Method GET -Path "/api/sso/clients/public/zdc-shop" -Validate $canonicalSsoClient
Invoke-HttpCheck -Name "api:sso-public-lottery" -Method GET -Path "/api/sso/clients/public/campus-lottery-station" -Validate $canonicalSsoClient
Invoke-HttpCheck -Name "api:sso-public-cdk" -Method GET -Path "/api/sso/clients/public/cdk-airdrop" -Validate $canonicalSsoClient
Invoke-R2CorsPreflightCheck
Invoke-R2CorsPreflightCheck -Name "r2:shop-media-cors-preflight" -Origin "https://shop.allinsong.top"

if ($script:SampleSectionId) {
    Invoke-HttpCheck -Name "api:section-detail" -Method GET -Path "/api/section/$script:SampleSectionId" -Validate $jsonOk
}
if ($script:SamplePostId) {
    Invoke-HttpCheck -Name "api:post-detail" -Method GET -Path "/api/post/$script:SamplePostId" -Validate $jsonOk
    Invoke-HttpCheck -Name "api:post-comments" -Method GET -Path "/api/comment/post/$script:SamplePostId?page=1&size=10" -Validate $jsonOk
    Invoke-HttpCheck -Name "api:post-similar" -Method GET -Path "/api/recommend/post-detail/$script:SamplePostId?limit=3" -Validate $jsonOk
    Invoke-HttpCheck -Name "api:post-poll" -Method GET -Path "/api/poll/by-post/$script:SamplePostId" -Validate $jsonOk
}
if ($script:SampleUserId) {
    Invoke-HttpCheck -Name "api:user-public-profile" -Method GET -Path "/api/user/public/$script:SampleUserId" -Validate $jsonOk
    Invoke-HttpCheck -Name "api:follow-stats" -Method GET -Path "/api/follow/stats/$script:SampleUserId" -Validate $jsonOk
}

Invoke-HttpCheck -Name "auth-guard:check-in-status" -Method GET -Path "/api/check-in/status" -ExpectedStatusCodes @(401, 403)
Invoke-HttpCheck -Name "auth-guard:check-in-post" -Method POST -Path "/api/check-in" -Body @{} -ExpectedStatusCodes @(401, 403)
Invoke-HttpCheck -Name "auth-guard:user-profile" -Method GET -Path "/api/user/profile" -ExpectedStatusCodes @(401, 403)
Invoke-HttpCheck -Name "auth-guard:points-summary" -Method GET -Path "/api/user/points/summary" -ExpectedStatusCodes @(401, 403)
Invoke-HttpCheck -Name "auth-guard:level-info" -Method GET -Path "/api/level/info" -ExpectedStatusCodes @(401, 403)
Invoke-HttpCheck -Name "auth-guard:trust-info" -Method GET -Path "/api/trust-level/info" -ExpectedStatusCodes @(401, 403)
Invoke-HttpCheck -Name "auth-guard:notifications" -Method GET -Path "/api/notification/unread-count" -ExpectedStatusCodes @(401, 403)
Invoke-HttpCheck -Name "auth-guard:direct-message" -Method GET -Path "/api/dm/unread-count" -ExpectedStatusCodes @(401, 403)
Invoke-HttpCheck -Name "auth-guard:admin-users" -Method GET -Path "/api/user/all?page=1&pageSize=1" -ExpectedStatusCodes @(401, 403)

Invoke-HttpCheck -Name "subsite:shop:index" -Method GET -Path "https://shop.allinsong.top/"
Invoke-HttpCheck -Name "subsite:shop:community-config" -Method GET -Path "https://shop.allinsong.top/api/auth/community-config" -Validate $canonicalCommunityConfig
Invoke-HttpCheck -Name "subsite:lottery:index" -Method GET -Path "https://lottery.allinsong.top/"
Invoke-HttpCheck -Name "subsite:lottery:health" -Method GET -Path "https://lottery.allinsong.top/api/health"
Invoke-HttpCheck -Name "subsite:cdk:index" -Method GET -Path "https://cdk.allinsong.top/"
Invoke-HttpCheck -Name "subsite:cdk:health" -Method GET -Path "https://cdk.allinsong.top/health"
Invoke-HttpCheck -Name "subsite:cdk:community-config" -Method GET -Path "https://cdk.allinsong.top/api/auth/community-config" -Validate $canonicalCommunityConfig
Invoke-HttpCheck -Name "subsite:nav:index" -Method GET -Path "https://nav.allinsong.top/"
Invoke-RedirectCheck -Name "edge:apex-redirect" -Uri "https://allinsong.top/" -ExpectedLocation "$($BaseUrl.TrimEnd('/'))/"

if ($Repair) {
    Invoke-CommandCheck -Name "backend:request-security-test" `
        -Command "mvn -q -Dtest=RequestSecurityFilterTest test" `
        -WorkingDirectory $Root
    Invoke-CommandCheck -Name "backend:package" `
        -Command "mvn -q -DskipTests package" `
        -WorkingDirectory $Root
    Invoke-CommandCheck -Name "frontend:build" `
        -Command "npm run build" `
        -WorkingDirectory (Join-Path $Root "web")
}

$EndedAt = Get-Date
$Failed = @($Checks | Where-Object { -not $_.ok })
$Report = [pscustomobject]@{
    startedAt = $StartedAt.ToString("o")
    endedAt   = $EndedAt.ToString("o")
    baseUrl   = $BaseUrl
    repair    = [bool]$Repair
    ok        = ($Failed.Count -eq 0)
    failed    = $Failed.Count
    checks    = $Checks
    log       = $TranscriptPath
}
$Report | ConvertTo-Json -Depth 8 | Set-Content -Path $ReportPath -Encoding UTF8

if ($Failed.Count -gt 0) {
    Write-Host "Production full check finished with $($Failed.Count) failing check(s). Report: $ReportPath"
    Stop-Transcript | Out-Null
    exit 2
}

Write-Host "Production full check finished cleanly. Report: $ReportPath"
Stop-Transcript | Out-Null
exit 0
