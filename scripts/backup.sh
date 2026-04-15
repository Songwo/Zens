#!/usr/bin/env bash
set -euo pipefail

# 用法:
# MYSQL_DATABASE=campus_pulse MYSQL_USER=root MYSQL_PASSWORD=xxx ./scripts/backup.sh
#
# 环境变量（可选）:
# BACKUP_ROOT=/opt/backups/campus-pulse
# RETENTION_DAYS=14
# MYSQL_HOST=127.0.0.1
# MYSQL_PORT=3306
# REDIS_HOST=127.0.0.1
# REDIS_PORT=6379
# REDIS_PASSWORD=
# REDIS_DUMP_PATH=/var/lib/redis/dump.rdb
# UPLOAD_DIR=/opt/campus-pulse/data/uploads

BACKUP_ROOT="${BACKUP_ROOT:-/opt/backups/campus-pulse}"
RETENTION_DAYS="${RETENTION_DAYS:-14}"
MYSQL_HOST="${MYSQL_HOST:-127.0.0.1}"
MYSQL_PORT="${MYSQL_PORT:-3306}"
MYSQL_DATABASE="${MYSQL_DATABASE:-}"
MYSQL_USER="${MYSQL_USER:-}"
MYSQL_PASSWORD="${MYSQL_PASSWORD:-}"
REDIS_HOST="${REDIS_HOST:-127.0.0.1}"
REDIS_PORT="${REDIS_PORT:-6379}"
REDIS_PASSWORD="${REDIS_PASSWORD:-}"
REDIS_DUMP_PATH="${REDIS_DUMP_PATH:-/var/lib/redis/dump.rdb}"
UPLOAD_DIR="${UPLOAD_DIR:-/opt/campus-pulse/data/uploads}"

TS="$(date +%Y%m%d_%H%M%S)"
TARGET_DIR="${BACKUP_ROOT}/${TS}"
mkdir -p "${TARGET_DIR}"

echo "[INFO] backup target: ${TARGET_DIR}"

if command -v mysqldump >/dev/null 2>&1 && [[ -n "${MYSQL_DATABASE}" && -n "${MYSQL_USER}" ]]; then
  echo "[INFO] backup mysql..."
  MYSQL_PWD="${MYSQL_PASSWORD}" mysqldump \
    -h"${MYSQL_HOST}" -P"${MYSQL_PORT}" -u"${MYSQL_USER}" \
    --single-transaction --set-gtid-purged=OFF "${MYSQL_DATABASE}" \
    | gzip > "${TARGET_DIR}/mysql_${MYSQL_DATABASE}.sql.gz"
else
  echo "[WARN] skip mysql backup (mysqldump not found or MYSQL_DATABASE/MYSQL_USER not set)"
fi

if command -v redis-cli >/dev/null 2>&1; then
  echo "[INFO] backup redis..."
  if [[ -n "${REDIS_PASSWORD}" ]]; then
    redis-cli -h "${REDIS_HOST}" -p "${REDIS_PORT}" -a "${REDIS_PASSWORD}" BGSAVE >/dev/null || true
  else
    redis-cli -h "${REDIS_HOST}" -p "${REDIS_PORT}" BGSAVE >/dev/null || true
  fi
  sleep 2
  if [[ -f "${REDIS_DUMP_PATH}" ]]; then
    cp "${REDIS_DUMP_PATH}" "${TARGET_DIR}/redis_dump.rdb"
  else
    echo "[WARN] redis dump path not found: ${REDIS_DUMP_PATH}"
  fi
else
  echo "[WARN] skip redis backup (redis-cli not found)"
fi

if [[ -d "${UPLOAD_DIR}" ]]; then
  echo "[INFO] backup uploads..."
  tar -czf "${TARGET_DIR}/uploads.tar.gz" -C "$(dirname "${UPLOAD_DIR}")" "$(basename "${UPLOAD_DIR}")"
else
  echo "[WARN] skip upload backup (dir not found: ${UPLOAD_DIR})"
fi

find "${BACKUP_ROOT}" -mindepth 1 -maxdepth 1 -type d -mtime +"${RETENTION_DAYS}" -exec rm -rf {} +
echo "[INFO] backup done"
