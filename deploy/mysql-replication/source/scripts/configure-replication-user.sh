#!/usr/bin/env bash
set -Eeuo pipefail

# shellcheck source=/dev/null
source /opt/campus-replication/common/validate-env.sh
validate_replication_environment

if [[ "${1:-}" == "--validate-only" ]]; then
  exit 0
fi

export MYSQL_PWD="${MYSQL_ROOT_PASSWORD}"

mysql --protocol=socket --user=root <<SQL
CREATE USER IF NOT EXISTS '${MYSQL_REPLICATION_USER}'@'%'
  IDENTIFIED WITH caching_sha2_password BY '${MYSQL_REPLICATION_PASSWORD}';
ALTER USER '${MYSQL_REPLICATION_USER}'@'%'
  IDENTIFIED WITH caching_sha2_password BY '${MYSQL_REPLICATION_PASSWORD}';
GRANT REPLICATION SLAVE, REPLICATION CLIENT ON *.*
  TO '${MYSQL_REPLICATION_USER}'@'%';
SQL
