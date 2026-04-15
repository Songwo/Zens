#!/usr/bin/env bash
set -euo pipefail

# 用法:
# ./scripts/rollback.sh [backup-jar-path]
#
# 环境变量（可选）:
# APP_NAME=campus-pulse
# DEPLOY_DIR=/opt/campus-pulse
# BACKUP_DIR=/opt/campus-pulse-backups
# JAVA_OPTS="-Xms512m -Xmx1024m"
# SERVICE_NAME=campus-pulse

APP_NAME="${APP_NAME:-campus-pulse}"
DEPLOY_DIR="${DEPLOY_DIR:-/opt/${APP_NAME}}"
BACKUP_DIR="${BACKUP_DIR:-/opt/${APP_NAME}-backups}"
LOG_DIR="${DEPLOY_DIR}/logs"
TARGET_JAR="${DEPLOY_DIR}/${APP_NAME}.jar"
JAVA_OPTS="${JAVA_OPTS:--Xms512m -Xmx1024m}"
SERVICE_NAME="${SERVICE_NAME:-}"

if [[ $# -ge 1 ]]; then
  ROLLBACK_JAR="$1"
else
  ROLLBACK_JAR="$(ls -1t "${BACKUP_DIR}/${APP_NAME}"_*.jar 2>/dev/null | head -n 1 || true)"
fi

if [[ -z "${ROLLBACK_JAR}" || ! -f "${ROLLBACK_JAR}" ]]; then
  echo "[ERROR] rollback jar not found"
  exit 1
fi

mkdir -p "${DEPLOY_DIR}" "${LOG_DIR}"
cp "${ROLLBACK_JAR}" "${TARGET_JAR}"
echo "[INFO] restore jar -> ${TARGET_JAR}"

if [[ -n "${SERVICE_NAME}" ]] && command -v systemctl >/dev/null 2>&1 && systemctl status "${SERVICE_NAME}" >/dev/null 2>&1; then
  systemctl restart "${SERVICE_NAME}"
else
  pkill -f "${APP_NAME}.jar" >/dev/null 2>&1 || true
  nohup java ${JAVA_OPTS} -jar "${TARGET_JAR}" > "${LOG_DIR}/app.out" 2>&1 &
fi

echo "[INFO] rollback done"
