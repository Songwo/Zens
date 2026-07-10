#!/usr/bin/env bash
set -Eeuo pipefail

# shellcheck source=/dev/null
source /opt/campus-replication/common/validate-env.sh
validate_replication_environment

readonly SEED_FILE='/replication-seed/campus-pulse-replication-seed.sql'
readonly TEMP_SEED_FILE="${SEED_FILE}.tmp"
export MYSQL_PWD="${MYSQL_ROOT_PASSWORD}"

umask 077
rm -f "${TEMP_SEED_FILE}"

mysqldump \
  --protocol=socket \
  --user=root \
  --single-transaction \
  --routines \
  --triggers \
  --events \
  --hex-blob \
  --set-gtid-purged=ON \
  --default-character-set=utf8mb4 \
  --databases campus_pulse \
  > "${TEMP_SEED_FILE}"

test -s "${TEMP_SEED_FILE}"
mv -f "${TEMP_SEED_FILE}" "${SEED_FILE}"
