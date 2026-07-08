$ErrorActionPreference = "Stop"

$composeFile = Join-Path $PSScriptRoot "docker-compose.yml"
$dumpFile = Join-Path $env:TEMP "campus-pulse-replication-seed.sql"

Write-Host "1/5 启动 source / replica 容器..." -ForegroundColor Cyan
docker compose -f $composeFile up -d

Write-Host "2/5 等待 source 就绪..." -ForegroundColor Cyan
docker compose -f $composeFile exec -T mysql-source sh -c "until mysqladmin ping -uroot -proot123456 --silent; do sleep 2; done"

Write-Host "3/5 等待 replica 就绪..." -ForegroundColor Cyan
docker compose -f $composeFile exec -T mysql-replica sh -c "until mysqladmin ping -uroot -proot123456 --silent; do sleep 2; done"

Write-Host "4/5 从 source 导出 campus_pulse 并导入 replica..." -ForegroundColor Cyan
docker compose -f $composeFile exec -T mysql-source sh -c "mysqldump -uroot -proot123456 --single-transaction --set-gtid-purged=ON --databases campus_pulse" | Out-File -FilePath $dumpFile -Encoding utf8
Get-Content -Raw $dumpFile | docker compose -f $composeFile exec -T mysql-replica mysql -uroot -proot123456

Write-Host "5/5 配置并启动复制..." -ForegroundColor Cyan
docker compose -f $composeFile exec -T mysql-replica mysql -uroot -proot123456 -e "STOP REPLICA; RESET REPLICA ALL; CHANGE REPLICATION SOURCE TO SOURCE_HOST='mysql-source', SOURCE_PORT=3306, SOURCE_USER='repl', SOURCE_PASSWORD='repl123456!', SOURCE_AUTO_POSITION=1, GET_SOURCE_PUBLIC_KEY=1; START REPLICA;"
docker compose -f $composeFile exec -T mysql-replica mysql -uroot -proot123456 -e "SHOW REPLICA STATUS\G"

Write-Host ""
Write-Host "完成。现在你可以用下面两个地址连接：" -ForegroundColor Green
Write-Host "  主库: 127.0.0.1:3307"
Write-Host "  从库: 127.0.0.1:3308"
Write-Host ""
Write-Host "验证建议：" -ForegroundColor Yellow
Write-Host "  1. 往 3307 的 campus_pulse 写一条测试数据"
Write-Host "  2. 到 3308 查询同一张表，确认数据同步过来了"
