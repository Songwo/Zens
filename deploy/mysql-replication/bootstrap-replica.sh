#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
COMPOSE_FILE="${SCRIPT_DIR}/docker-compose.yml"
DUMP_FILE="${TMPDIR:-/tmp}/campus-pulse-replication-seed.sql"

echo "1/5 启动 source / replica 容器..."
docker compose -f "${COMPOSE_FILE}" up -d

echo "2/5 等待 source 就绪..."
docker compose -f "${COMPOSE_FILE}" exec -T mysql-source sh -c "until mysqladmin ping -uroot -proot123456 --silent; do sleep 2; done"

echo "3/5 等待 replica 就绪..."
docker compose -f "${COMPOSE_FILE}" exec -T mysql-replica sh -c "until mysqladmin ping -uroot -proot123456 --silent; do sleep 2; done"

echo "4/5 从 source 导出 campus_pulse 并导入 replica..."
docker compose -f "${COMPOSE_FILE}" exec -T mysql-source sh -c "mysqldump -uroot -proot123456 --single-transaction --set-gtid-purged=ON --databases campus_pulse" > "${DUMP_FILE}"
docker compose -f "${COMPOSE_FILE}" exec -T mysql-replica mysql -uroot -proot123456 < "${DUMP_FILE}"

echo "5/5 配置并启动复制..."
docker compose -f "${COMPOSE_FILE}" exec -T mysql-replica mysql -uroot -proot123456 -e "STOP REPLICA; RESET REPLICA ALL; CHANGE REPLICATION SOURCE TO SOURCE_HOST='mysql-source', SOURCE_PORT=3306, SOURCE_USER='repl', SOURCE_PASSWORD='repl123456!', SOURCE_AUTO_POSITION=1, GET_SOURCE_PUBLIC_KEY=1; START REPLICA;"
docker compose -f "${COMPOSE_FILE}" exec -T mysql-replica mysql -uroot -proot123456 -e "SHOW REPLICA STATUS\G"

echo
echo "完成。主库: 127.0.0.1:3307  从库: 127.0.0.1:3308"
