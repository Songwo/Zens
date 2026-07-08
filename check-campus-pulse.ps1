<#
.SYNOPSIS
  Campus Pulse 主站 + 子站 运行状态检测与补齐脚本
.DESCRIPTION
  一键检测主站/子站所有服务的运行状态，并自动补齐数据库(SSO 注册)、CDK 种子数据、
  配置一致性等缺失项。默认检测+自动补齐安全项；-CheckOnly 仅检测不改动。
.PARAMETER CheckOnly
  只检测、打印报告，不做任何修复。
.PARAMETER StartServices
  检测到关键基础设施(MySQL/Redis)或服务未运行时尝试拉起(仅本机 dev)。
.EXAMPLE
  .\check-campus-pulse.ps1              # 检测 + 自动补齐
  .\check-campus-pulse.ps1 -CheckOnly   # 只看状态
#>
[CmdletBinding()]
param(
  [switch]$CheckOnly,
  [switch]$StartServices
)

$ErrorActionPreference = 'Continue'
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$OutputEncoding           = [System.Text.Encoding]::UTF8

# ─── 路径与全局配置 ────────────────────────────────────────────
$Root = 'D:\2026毕业设计\DaiMa\campus-pulse(back)\campus-pulse'
$MainEnv      = Join-Path $Root '.env.local'
$ShopEnv      = Join-Path $Root 'zdc-shop\.env.local'
$CdkStateJson = Join-Path $Root 'cdk-airdrop-station\server\data\state.json'

# 默认 SSO 客户端需注册的本地回调(dev)。生产域名由部署侧维护，这里只补 dev 端口。
$SsoDevUris = @{
  'cdk-airdrop'            = @('http://localhost:5174/login/callback','http://127.0.0.1:5174/login/callback')
  'campus-lottery-station' = @('http://localhost:5175/api/auth/sso/callback','http://127.0.0.1:5175/api/auth/sso/callback')
  'zdc-shop'               = @('http://localhost:3000/login/callback','http://127.0.0.1:3000/login/callback')
}

# 服务清单：端口 + 健康探针
$Services = @(
  @{N='MySQL';            Port=3306; Url=$null}
  @{N='Redis';            Port=6379; Url=$null}
  @{N='主站后端(7800)';    Port=7800; Url='http://127.0.0.1:7800/actuator/health'}
  @{N='Go媒体(8090)';     Port=8090; Url='http://127.0.0.1:8090/health'}
  @{N='CDK后端(8088)';    Port=8088; Url='http://127.0.0.1:8088/health'}
  @{N='抽奖后端(8093)';   Port=8093; Url='http://127.0.0.1:8093/api/bootstrap'}
  @{N='主站前端(5173)';    Port=5173; Url='http://127.0.0.1:5173'}
  @{N='CDK前端(5174)';    Port=5174; Url='http://127.0.0.1:5174'}
  @{N='抽奖前端(5175)';   Port=5175; Url='http://127.0.0.1:5175/api/bootstrap'}
  @{N='导航站(5176)';     Port=5176; Url='http://127.0.0.1:5176'}
  @{N='积分商城(3000)';   Port=3000; Url='http://127.0.0.1:3000'}
)

$Report = New-Object System.Collections.Generic.List[object]
function Add($name,$status,$detail){ $Report.Add([pscustomobject]@{项目=$name;状态=$status;说明=$detail}) }

# ─── 通用工具 ──────────────────────────────────────────────────
function Read-EnvFile($path){
  $h=@{}
  if(-not (Test-Path $path)){ return $h }
  foreach($line in Get-Content $path){
    if($line -match '^\s*#|^\s*$'){ continue }
    if($line -match '^\s*([^=]+?)\s*=\s*(.*)$'){
      $k=$Matches[1].Trim(); $v=$Matches[2].Trim().Trim('"').Trim("'")
      $h[$k]=$v
    }
  }
  return $h
}
function Find-MysqlExe{
  $c = Get-Command mysql -ErrorAction SilentlyContinue
  if($c){ return $c.Source }
  $hits = @(
    'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe',
    'C:\Program Files\MySQL\MySQL Server 8.4\bin\mysql.exe'
  ) | Where-Object { Test-Path $_ }
  if($hits){ return $hits[0] }
  Get-ChildItem 'C:\Program Files\MySQL\*\bin\mysql.exe' -ErrorAction SilentlyContinue | Select-Object -First 1 -Expand FullName
}
function Invoke-Mysql($sql){
  $exe = Find-MysqlExe
  if(-not $exe){ return $null }
  $env = Read-EnvFile $MainEnv
  $pw = $env['DB_PASSWORD']; if(-not $pw){ $pw='123456' }
  $args = @('-uroot',"--password=$pw",'--default-character-set=utf8mb4','-N','-B','-e',$sql)
  & $exe @args 2>$null
}
function Test-PortUp($port){
  $c = New-Object Net.Sockets.TcpClient
  try { $c.Connect('127.0.0.1',$port); return $true } catch { return $false } finally { $c.Close() }
}
function Http-Ok($url){
  try { $r = Invoke-WebRequest $url -UseBasicParsing -TimeoutSec 5; return $r.StatusCode -lt 500 } catch { return $false }
}
function B64Url($bytes){ [Convert]::ToBase64String($bytes).TrimEnd('=').Replace('+','-').Replace('/','_') }
function New-SsoToken($secret,$payload){
  $h = B64Url ([Text.Encoding]::UTF8.GetBytes(($payload.Header | ConvertTo-Json -Compress)))
  $p = B64Url ([Text.Encoding]::UTF8.GetBytes(($payload.Body   | ConvertTo-Json -Compress)))
  $m = New-Object Security.Cryptography.HMACSHA256
  $m.Key = [Text.Encoding]::UTF8.GetBytes($secret)
  $s = B64Url ($m.ComputeHash([Text.Encoding]::UTF8.GetBytes("$h.$p")))
  "$h.$p.$s"
}
function Post-Json($url,$bodyObj,$token=$null){
  $json = $bodyObj | ConvertTo-Json -Compress -Depth 10
  $bytes = [Text.Encoding]::UTF8.GetBytes($json)
  $h = @{ 'Content-Type'='application/json; charset=utf-8' }
  if($token){ $h['Authorization']="Bearer $token" }
  try { return (Invoke-RestMethod $url -Method Post -Body $bytes -Headers $h -TimeoutSec 10) }
  catch { return $null }
}
function Put-Json($url,$bodyObj,$token){
  $json = $bodyObj | ConvertTo-Json -Compress -Depth 10
  $bytes = [Text.Encoding]::UTF8.GetBytes($json)
  $h = @{ 'Content-Type'='application/json; charset=utf-8'; 'Authorization'="Bearer $token" }
  try { return (Invoke-RestMethod $url -Method Put -Body $bytes -Headers $h -TimeoutSec 10) }
  catch { return $null }
}
function Get-Json($url,$token=$null){
  $h = @{}; if($token){ $h['Authorization']="Bearer $token" }
  try { return (Invoke-RestMethod $url -Headers $h -TimeoutSec 8) } catch { return $null }
}

# ─── 1. 服务端口 + 健康探针 ───────────────────────────────────
Write-Host "`n[1/6] 服务运行状态" -ForegroundColor Cyan
foreach($s in $Services){
  $up = Test-PortUp $s.Port
  $httpOk = $false
  if($up -and $s.Url){ $httpOk = Http-Ok $s.Url }
  if(-not $up){
    Add $s.N '✗未运行' "端口 $($s.Port) 未监听"
    Write-Host ("  ✗ {0,-16} 端口{1} 未监听" -f $s.N,$s.Port) -ForegroundColor Red
  } elseif($s.Url -and -not $httpOk){
    Add $s.N '⚠异常'   "端口开但健康探针失败"
    Write-Host ("  ⚠ {0,-16} 端口开但探针失败" -f $s.N) -ForegroundColor Yellow
  } else {
    Add $s.N '✓正常' "$(if($s.Url){'探针OK'}else{'端口OK'})"
    Write-Host ("  ✓ {0,-16} 正常" -f $s.N) -ForegroundColor Green
  }
}

# ─── 2. MySQL 数据库连通 + SSO 客户端注册补齐 ─────────────────
Write-Host "`n[2/6] 数据库 / SSO 客户端注册" -ForegroundColor Cyan
if(-not (Test-PortUp 3306)){
  Add 'MySQL' '✗未运行' '3306 未监听，跳过 DB 检查'
  Write-Host "  ✗ MySQL 未运行，跳过" -ForegroundColor Red
} else {
  $cnt = Invoke-Mysql "SELECT COUNT(*) FROM campus_pulse.sys_sso_client"
  if($null -eq $cnt){
    Add 'DB连通' '✗失败' '查询失败(密码/库?请看 .env.local DB_PASSWORD)'
    Write-Host "  ✗ DB 查询失败" -ForegroundColor Red
  } else {
    Add 'DB连通' '✓正常' "sys_sso_client 共 $cnt 个客户端"
    Write-Host "  ✓ DB 连通，SSO 客户端 $cnt 个" -ForegroundColor Green
    # 逐个客户端补齐 dev 回调
    foreach($cid in $SsoDevUris.Keys){
      $row = Invoke-Mysql "SELECT redirect_uri FROM campus_pulse.sys_sso_client WHERE client_id='$cid'"
      if(-not $row){ Add "SSO:$cid" '✗缺失' "主站未注册该 client"; Write-Host "  ✗ $cid 未注册" -ForegroundColor Red; continue }
      $existing = $row -join ','
      $missing = $SsoDevUris[$cid] | Where-Object { $existing -notlike "*$_*" }
      if($missing.Count -eq 0){
        Add "SSO:$cid" '✓正常' 'dev 回调齐全'
        Write-Host "  ✓ $cid dev 回调齐全" -ForegroundColor Green
      } elseif($CheckOnly){
        Add "SSO:$cid" '⚠缺回调' "缺: $($missing -join ', ')"
        Write-Host "  ⚠ $cid 缺回调(仅检测未补): $($missing -join ', ')" -ForegroundColor Yellow
      } else {
        $new = ($existing.Trim().TrimEnd(',') + ',' + ($missing -join ',')) -replace '^,',''
        Invoke-Mysql "UPDATE campus_pulse.sys_sso_client SET redirect_uri='$($new -replace "'","''")' WHERE client_id='$cid'" | Out-Null
        Add "SSO:$cid" '✓已补齐' "新增 $($missing.Count) 条 dev 回调"
        Write-Host "  ↺ $cid 已补 $($missing.Count) 条 dev 回调" -ForegroundColor Cyan
      }
    }
  }
}

# ─── 3. CDK 默认领取节点种子补齐 ──────────────────────────────
Write-Host "`n[3/6] CDK 默认领取节点(freshman-2026)" -ForegroundColor Cyan
if(-not (Test-PortUp 8088)){
  Add 'CDK节点' '✗未运行' 'CDK 8088 未监听'
  Write-Host "  ✗ CDK 未运行，跳过" -ForegroundColor Red
} else {
  $node = Get-Json 'http://127.0.0.1:8088/api/public/claim/freshman-2026'
  if($node -and $node.success -and $node.data){
    $st = $node.data.status; $rem = $node.data.remaining; $tot = $node.data.totalStock
    $bad = ($node.data.name -match '�')
    if($bad){
      if($CheckOnly){ Add 'CDK节点' '⚠乱码' '节点存在但中文乱码'; Write-Host '  ⚠ 节点中文乱码(仅检测)' -ForegroundColor Yellow }
      else { Add 'CDK节点' '⚠乱码' '节点存在但中文乱码，需手动用 UTF-8 重置(见说明)'; Write-Host '  ⚠ 节点中文乱码' -ForegroundColor Yellow }
    } else {
      Add 'CDK节点' '✓正常' "status=$st 库存=$rem/$tot"
      Write-Host "  ✓ 节点存在 status=$st 库存=$rem/$tot" -ForegroundColor Green
    }
  } elseif(-not $CheckOnly){
    Write-Host "  ↺ 节点缺失，开始播种..." -ForegroundColor Cyan
    $env = Read-EnvFile $MainEnv
    $jwt = $env['JWT_SECRET']
    if(-not $jwt){ Add 'CDK节点' '✗失败' '主站 .env.local 无 JWT_SECRET'; Write-Host '  ✗ 无 JWT_SECRET' -ForegroundColor Red }
    else {
      $now=[int][double]::Parse((Get-Date -UFormat %s))
      $payload = @{ Header=@{alg='HS256';typ='JWT'}; Body=[ordered]@{
        sub='2027231167307214850'; username='Song'; nickname='zens'; sso=$true
        client_id='cdk-airdrop'; roles=@('ROLE_ADMIN'); iat=$now; exp=$now+600 } }
      $sso = New-SsoToken $jwt $payload
      $login = Post-Json 'http://127.0.0.1:8088/api/auth/community-login' @{ssoToken=$sso}
      if(-not $login -or -not $login.data -or -not $login.data.token){ Add 'CDK节点' '✗失败' 'admin SSO 换 token 失败'; Write-Host '  ✗ SSO 换 token 失败' -ForegroundColor Red }
      else {
        $tk = $login.data.token
        $p = Post-Json 'http://127.0.0.1:8088/api/admin/projects' @{name='2026开学季社区福利';description='面向 Zens 社区新成员的开学季 CDK 福利发放'} $tk
        $pid = $p.data.id
        $past = (Get-Date).AddDays(-1).ToUniversalTime().ToString('yyyy-MM-ddTHH:mm:ssZ')
        $end  = (Get-Date).AddDays(30).ToUniversalTime().ToString('yyyy-MM-ddTHH:mm:ssZ')
        $c = Post-Json 'http://127.0.0.1:8088/api/admin/campaigns' @{projectId=$pid;name='开学季开发工具包';description='社区活跃成员可领取开发工具 CDK';startAt=$past;endAt=$end;perUserLimit=1;enabled=$true;rewardList=@();rules='每个社区账号限领一次。'} $tk
        $cid = $c.data.id
        Post-Json 'http://127.0.0.1:8088/api/admin/nodes' @{projectId=$pid;campaignId=$cid;name='开学季开发工具 CDK';slug='freshman-2026';status='active';title='立即领取开学季开发工具 CDK';description='面向 Zens 社区新成员的限量开发工具福利，使用社区账号登录后即可领取，兑换码绑定到当前账号。';buttonText='立即领取福利';showStock=$true;showEndTime=$true;limit=100} $tk | Out-Null
        Post-Json 'http://127.0.0.1:8088/api/admin/cdks/import' @{projectId=$pid;campaignId=$cid;codes=@('ZENS-2026-FRESH-001','ZENS-2026-FRESH-002','ZENS-2026-FRESH-003','ZENS-2026-FRESH-004','ZENS-2026-FRESH-005')} $tk | Out-Null
        Add 'CDK节点' '✓已补齐' '新建 project/campaign/node + 5 CDK'
        Write-Host '  ↺ 已播种 project/campaign/node + 5 CDK' -ForegroundColor Cyan
      }
    }
  } else { Add 'CDK节点' '✗缺失' 'freshman-2026 不存在'; Write-Host '  ✗ 节点缺失(仅检测)' -ForegroundColor Red }
}

# ─── 4. 配置文件一致性 ────────────────────────────────────────
Write-Host "`n[4/6] 配置一致性" -ForegroundColor Cyan
$main = Read-EnvFile $MainEnv; $shop = Read-EnvFile $ShopEnv
function Cmp($label,$a,$b,$aSrc,$bSrc){
  if(-not $a -or -not $b){ Add $label '⚠无法比对' "$aSrc / $bSrc 缺值"; Write-Host "  ⚠ $label 缺值" -ForegroundColor Yellow; return }
  if($a -eq $b){ Add $label '✓一致' ''; Write-Host "  ✓ $label 一致" -ForegroundColor Green }
  else { Add $label '✗不一致' "$aSrc ≠ $bSrc"; Write-Host "  ✗ $label 不一致($aSrc ≠ $bSrc)" -ForegroundColor Red }
}
Cmp 'JWT_SECRET(主站vs商城)' $main['JWT_SECRET'] $shop['JWT_SECRET'] '.env.local' 'zdc-shop/.env.local'
Cmp 'SHOP_SERVICE_SECRET'   $main['SHOP_SERVICE_SECRET'] $shop['SHOP_SERVICE_SECRET'] '.env.local' 'zdc-shop/.env.local'

# ─── 5. CDK ↔ 主站 JWT 联通性(端到端) ─────────────────────────
Write-Host "`n[5/6] CDK↔主站 SSO 联通" -ForegroundColor Cyan
if((Test-PortUp 8088) -and $main['JWT_SECRET']){
  $now=[int][double]::Parse((Get-Date -UFormat %s))
  $payload = @{ Header=@{alg='HS256';typ='JWT'}; Body=[ordered]@{
    sub='linkage-test'; username='linktest'; sso=$true; client_id='cdk-airdrop'
    roles=@('ROLE_USER'); iat=$now; exp=$now+60 } }
  $sso = New-SsoToken $main['JWT_SECRET'] $payload
  $r = Post-Json 'http://127.0.0.1:8088/api/auth/community-login' @{ssoToken=$sso}
  if($r -and $r.success){ Add 'CDK验证主站JWT' '✓通' 'CDK 能用主站 JWT_SECRET 验证 SSO token'; Write-Host '  ✓ CDK 已正确配置 MAIN_SITE_JWT_SECRET' -ForegroundColor Green }
  else { Add 'CDK验证主站JWT' '✗不通' 'CDK 缺 MAIN_SITE_JWT_SECRET 或与主站不一致'; Write-Host '  ✗ CDK 的 MAIN_SITE_JWT_SECRET 未配置/不一致' -ForegroundColor Red }
} else { Add 'CDK验证主站JWT' '⚠跳过' 'CDK 未运行或主站无 JWT_SECRET'; Write-Host '  ⚠ 跳过' -ForegroundColor Yellow }

# ─── 6. 抽奖站↔主站 SSO 起点连通 ─────────────────────────────
Write-Host "`n[6/6] 抽奖站 SSO 起点" -ForegroundColor Cyan
if(Test-PortUp 8093){
  try {
    $resp = Invoke-WebRequest 'http://127.0.0.1:8093/api/auth/sso/start' -UseBasicParsing -TimeoutSec 5 -MaximumRedirection 0 -ErrorAction Stop
  } catch { $resp = $_.Exception.Response }
  $loc = $resp.Headers.Location
  if($loc -like '*localhost:5173/sso/authorize*'){ Add '抽奖SSO起点' '✓正常' '302 -> 主站授权页'; Write-Host '  ✓ 302 -> 主站授权' -ForegroundColor Green }
  else { Add '抽奖SSO起点' '⚠异常' "Location=$loc"; Write-Host "  ⚠ 异常 Location=$loc" -ForegroundColor Yellow }
} else { Add '抽奖SSO起点' '✗未运行' '8093 未监听'; Write-Host '  ✗ 抽奖未运行' -ForegroundColor Red }

# ─── 可选：拉起缺失的基础设施 ─────────────────────────────────
if($StartServices -and -not (Test-PortUp 6379)){
  $redis = 'D:\Redis\redis-server.exe'
  if(Test-Path $redis){ Start-Process $redis -WindowStyle Hidden; Start-Sleep 2; Add 'Redis' '↺已拉起' ''; Write-Host "`n↺ 已拉起 Redis" -ForegroundColor Cyan }
}

# ─── 汇总 ─────────────────────────────────────────────────────
Write-Host "`n================ 汇总 ================" -ForegroundColor Magenta
$Report | Format-Table -AutoSize
$bad = ($Report | Where-Object { $_.状态 -like '✗*' }).Count
$warn = ($Report | Where-Object { $_.状态 -like '⚠*' }).Count
Write-Host ("合计: {0} 正常 / {1} 警告 / {2} 异常" -f ($Report.Count-$bad-$warn),$warn,$bad) -ForegroundColor Magenta
if($CheckOnly){ Write-Host "(仅检测模式，未做任何改动)" -ForegroundColor DarkGray }
