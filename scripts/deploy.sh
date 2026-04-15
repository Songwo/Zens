#!/usr/bin/env bash
set -euo pipefail

# 用法:
# ./scripts/deploy.sh /path/to/campus-pulse.jar
#
# 环境变量（可选）:
# APP_NAME=campus-pulse
# DEPLOY_DIR=/opt/campus-pulse
# BACKUP_DIR=/opt/campus-pulse-backups
# HEALTH_URL=http://127.0.0.1:7800/actuator/health
# JAVA_OPTS="-Xms512m -Xmx1024m"
# SERVICE_NAME=campus-pulse

if [[ $# -lt 1 ]]; then
  echo "Usage: $0 <jar-path>"
  exit 1
fi

JAR_SOURCE="$1"
APP_NAME="${APP_NAME:-campus-pulse}"
DEPLOY_DIR="${DEPLOY_DIR:-/opt/${APP_NAME}}"
BACKUP_DIR="${BACKUP_DIR:-/opt/${APP_NAME}-backups}"
LOG_DIR="${DEPLOY_DIR}/logs"
TARGET_JAR="${DEPLOY_DIR}/${APP_NAME}.jar"
HEALTH_URL="${HEALTH_URL:-http://127.0.0.1:7800/actuator/health}"
JAVA_OPTS="${JAVA_OPTS:--Xms512m -Xmx1024m}"
SERVICE_NAME="${SERVICE_NAME:-}"

if [[ ! -f "${JAR_SOURCE}" ]]; then
  echo "[ERROR] jar not found: ${JAR_SOURCE}"
  exit 1
fi

mkdir -p "${DEPLOY_DIR}" "${BACKUP_DIR}" "${LOG_DIR}"
TIMESTAMP="$(date +%Y%m%d_%H%M%S)"
BACKUP_JAR="${BACKUP_DIR}/${APP_NAME}_${TIMESTAMP}.jar"

if [[ -f "${TARGET_JAR}" ]]; then
  cp "${TARGET_JAR}" "${BACKUP_JAR}"
  echo "[INFO] backup current jar -> ${BACKUP_JAR}"
fi

cp "${JAR_SOURCE}" "${TARGET_JAR}"
echo "[INFO] deployed jar -> ${TARGET_JAR}"

start_app() {
  if [[ -n "${SERVICE_NAME}" ]] && command -v systemctl >/dev/null 2>&1 && systemctl status "${SERVICE_NAME}" >/dev/null 2>&1; then
    systemctl restart "${SERVICE_NAME}"
    return
  fi

  pkill -f "${APP_NAME}.jar" >/dev/null 2>&1 || true
  nohup java ${JAVA_OPTS} -jar "${TARGET_JAR}" > "${LOG_DIR}/app.out" 2>&1 &
}

rollback() {
  if [[ ! -f "${BACKUP_JAR}" ]]; then
    echo "[ERROR] no backup jar found, cannot rollback automatically"
    exit 1
  fi
  cp "${BACKUP_JAR}" "${TARGET_JAR}"
  start_app
  echo "[WARN] rollback executed: ${BACKUP_JAR}"
}

start_app

echo "[INFO] waiting for health check: ${HEALTH_URL}"
for i in {1..30}; do
  if curl -fsS "${HEALTH_URL}" >/dev/null 2>&1; then
    echo "[INFO] deploy success"
    exit 0
  fi
  sleep 2
done

echo "[ERROR] health check failed"
rollback
exit 1
