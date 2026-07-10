#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly SCRIPT_DIR
readonly COMPOSE_FILE="${SCRIPT_DIR}/docker-compose.yml"
readonly ENV_FILE="${SCRIPT_DIR}/.env"
readonly CONTAINER_SEED_FILE='/replication-seed/campus-pulse-replication-seed.sql'

if [[ ! -f "${ENV_FILE}" ]]; then
  echo "Missing ${ENV_FILE}. Copy .env.example to .env and replace every placeholder." >&2
  exit 1
fi

compose() {
  docker compose \
    --project-directory "${SCRIPT_DIR}" \
    --env-file "${ENV_FILE}" \
    --file "${COMPOSE_FILE}" \
    "$@"
}

cleanup() {
  compose exec -T mysql-replica bash \
    /opt/campus-replication/replica/manage-replica.sh read-only >/dev/null 2>&1 || true
  compose exec -T mysql-source rm -f -- "${CONTAINER_SEED_FILE}" >/dev/null 2>&1 || true
  compose exec -T mysql-replica rm -f -- "${CONTAINER_SEED_FILE}" >/dev/null 2>&1 || true
}
trap cleanup EXIT

echo "1/7 Validate the local demonstration configuration..."
compose config --quiet
compose run --rm --no-deps --entrypoint bash mysql-source \
  /opt/campus-replication/source/configure-replication-user.sh --validate-only
compose run --rm --no-deps --entrypoint bash mysql-replica \
  /opt/campus-replication/replica/manage-replica.sh --validate-only

echo "2/7 Start the source and replica containers..."
compose up --detach --wait --wait-timeout 180

echo "3/7 Create or rotate the dedicated replication user..."
compose exec -T mysql-source bash \
  /opt/campus-replication/source/configure-replication-user.sh

echo "4/7 Create a consistent GTID seed from campus_pulse..."
compose exec -T mysql-source bash \
  /opt/campus-replication/source/create-seed.sh

echo "5/7 Re-seed the local replica..."
compose exec -T mysql-replica bash \
  /opt/campus-replication/replica/manage-replica.sh prepare
compose exec -T mysql-replica bash \
  /opt/campus-replication/replica/manage-replica.sh import

echo "6/7 Configure GTID replication and restore strict read-only mode..."
compose exec -T mysql-replica bash \
  /opt/campus-replication/replica/manage-replica.sh configure

echo "7/7 Verify replication, read-only flags, and the database filter..."
compose exec -T mysql-replica bash \
  /opt/campus-replication/replica/manage-replica.sh verify
compose exec -T mysql-replica bash \
  /opt/campus-replication/replica/manage-replica.sh status

echo
echo "Local demonstration is ready. Connection ports are defined in ${ENV_FILE}."
echo "The replica accepts campus_pulse reads only; keep all writes on the source."
