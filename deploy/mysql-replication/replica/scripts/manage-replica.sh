#!/usr/bin/env bash
set -Eeuo pipefail

# shellcheck source=/dev/null
source /opt/campus-replication/common/validate-env.sh
validate_replication_environment

if [[ "${1:-}" == "--validate-only" ]]; then
  exit 0
fi

readonly SEED_FILE='/replication-seed/campus-pulse-replication-seed.sql'
export MYSQL_PWD="${MYSQL_ROOT_PASSWORD}"

case "${1:-}" in
  prepare)
    # STOP REPLICA reports an error on a never-configured instance. RESET below
    # still fails safely if a genuinely running channel could not be stopped.
    mysql --user=root --execute="STOP REPLICA" 2>/dev/null || true

    mysql --user=root <<'SQL'
RESET REPLICA ALL;
SET GLOBAL super_read_only = OFF;
SET GLOBAL read_only = OFF;
SET SESSION sql_log_bin = OFF;
DROP DATABASE IF EXISTS campus_pulse;
RESET BINARY LOGS AND GTIDS;
SQL
    ;;
  import)
    if [[ ! -s "${SEED_FILE}" ]]; then
      echo "Replica seed file is missing or empty: ${SEED_FILE}" >&2
      exit 1
    fi
    mysql --user=root < "${SEED_FILE}"
    ;;
  configure)
    mysql --user=root <<SQL
RESET REPLICA ALL;
CHANGE REPLICATION SOURCE TO
  SOURCE_HOST = 'mysql-source',
  SOURCE_PORT = 3306,
  SOURCE_USER = '${MYSQL_REPLICATION_USER}',
  SOURCE_PASSWORD = '${MYSQL_REPLICATION_PASSWORD}',
  SOURCE_AUTO_POSITION = 1,
  GET_SOURCE_PUBLIC_KEY = 1;
START REPLICA;
SET GLOBAL read_only = ON;
SET GLOBAL super_read_only = ON;
SQL
    ;;
  verify)
    replication_ready=0
    for _ in {1..30}; do
      replication_ready="$(mysql --user=root --batch --skip-column-names <<'SQL'
SELECT IF(
  COALESCE((SELECT SERVICE_STATE
            FROM performance_schema.replication_connection_status
            WHERE CHANNEL_NAME = ''), 'OFF') = 'ON'
  AND COALESCE((SELECT SERVICE_STATE
                FROM performance_schema.replication_applier_status
                WHERE CHANNEL_NAME = ''), 'OFF') = 'ON',
  1,
  0
);
SQL
      )"

      if [[ "${replication_ready}" == "1" ]]; then
        break
      fi
      sleep 2
    done

    if [[ "${replication_ready}" != "1" ]]; then
      echo "Replication did not become healthy within 60 seconds." >&2
      mysql --user=root --execute="SHOW REPLICA STATUS\G" >&2
      exit 1
    fi

    read_only_state="$(mysql --user=root --batch --skip-column-names \
      --execute="SELECT CONCAT(@@GLOBAL.read_only, ':', @@GLOBAL.super_read_only)")"
    if [[ "${read_only_state}" != "1:1" ]]; then
      echo "Replica read_only and super_read_only are not both enabled." >&2
      exit 1
    fi

    table_filter="$(mysql --user=root --batch --skip-column-names \
      --execute="SELECT @@GLOBAL.replicate_wild_do_table")"
    if [[ "${table_filter}" != "campus_pulse.%" ]]; then
      echo "Replica filter must be exactly campus_pulse.%." >&2
      exit 1
    fi
    ;;
  read-only)
    mysql --user=root \
      --execute="SET GLOBAL read_only = ON; SET GLOBAL super_read_only = ON"
    ;;
  status)
    mysql --user=root --execute="SHOW REPLICA STATUS\G"
    ;;
  *)
    echo "Usage: $0 {--validate-only|prepare|import|configure|verify|read-only|status}" >&2
    exit 2
    ;;
esac
