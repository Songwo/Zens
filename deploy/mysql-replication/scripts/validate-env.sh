#!/usr/bin/env bash

validate_replication_environment() {
  local password_pattern='^[A-Za-z0-9._~!@#%^*+=:,/?-]{16,128}$'
  local user_pattern='^[A-Za-z][A-Za-z0-9_]{0,31}$'

  if [[ -z "${MYSQL_ROOT_PASSWORD:-}" ]]; then
    echo "MYSQL_ROOT_PASSWORD is required for this container." >&2
    return 1
  fi

  if (( ${#MYSQL_ROOT_PASSWORD} < 16 || ${#MYSQL_ROOT_PASSWORD} > 128 )); then
    echo "The root password must contain 16 to 128 characters." >&2
    return 1
  fi

  if [[ "${MYSQL_ROOT_PASSWORD}" == replace-* ]]; then
    echo "Replace the root-password placeholder in deploy/mysql-replication/.env." >&2
    return 1
  fi

  if [[ ! "${MYSQL_REPLICATION_USER:-}" =~ $user_pattern ]]; then
    echo "MYSQL_REPLICATION_USER must start with a letter and contain at most 32 letters, digits, or underscores." >&2
    return 1
  fi

  if [[ "${MYSQL_REPLICATION_USER}" == replace_* || "${MYSQL_REPLICATION_USER}" == "root" ]]; then
    echo "Replace MYSQL_REPLICATION_USER with a dedicated, non-root replication user." >&2
    return 1
  fi

  if [[ ! "${MYSQL_REPLICATION_PASSWORD:-}" =~ $password_pattern ]]; then
    echo "MYSQL_REPLICATION_PASSWORD must contain 16 to 128 safe ASCII characters." >&2
    return 1
  fi

  if [[ "${MYSQL_REPLICATION_PASSWORD}" == replace-* ]]; then
    echo "Replace the replication-password placeholder in deploy/mysql-replication/.env." >&2
    return 1
  fi

  if [[ "${MYSQL_REPLICATION_PASSWORD}" == "${MYSQL_ROOT_PASSWORD}" ]]; then
    echo "The replication password must be different from the container root password." >&2
    return 1
  fi
}
