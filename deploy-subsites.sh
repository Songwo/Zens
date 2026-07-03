#!/usr/bin/env bash
set -Eeuo pipefail

# Zens sub-sites one-click deployment.
# It deploys only sub-sites and never restarts the main community service.
#
# Default domains:
#   main:    https://allinsong.top
#   shop:    https://shop.allinsong.top
#   lottery: https://lottery.allinsong.top
#   cdk:     https://cdk.allinsong.top
#
# Usage:
#   bash deploy-subsites.sh
#
# Useful overrides:
#   MAIN_ENV_FILE=.env.production bash deploy-subsites.sh
#   MAIN_SITE_BACKEND_URL=https://allinsong.top RUN_SHOP_SEED=true bash deploy-subsites.sh

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
STAMP="$(date +%Y%m%d%H%M%S)"

SHOP_DIR="${ROOT_DIR}/zdc-shop"
LOTTERY_DIR="${ROOT_DIR}/campus-lottery-station"
CDK_DIR="${ROOT_DIR}/cdk-airdrop-station"

declare -A MAIN_ENV=()
MAIN_ENV_FILES=()
MAIN_ENV_BACKUP_FILE=""

MAIN_DB_NAME=""
MYSQL_HOST=""
MYSQL_PORT=""
MYSQL_USER=""
MYSQL_PASSWORD=""

log() { printf '\033[1;34m[zens-subsites]\033[0m %s\n' "$*"; }
warn() { printf '\033[1;33m[warn]\033[0m %s\n' "$*" >&2; }
die() { printf '\033[1;31m[error]\033[0m %s\n' "$*" >&2; exit 1; }

is_true() {
  case "${1:-}" in
    1|true|TRUE|yes|YES|on|ON) return 0 ;;
    *) return 1 ;;
  esac
}

need() {
  command -v "$1" >/dev/null 2>&1 || die "missing command: $1"
}

trim() {
  local v="$1"
  v="${v#"${v%%[![:space:]]*}"}"
  v="${v%"${v##*[![:space:]]}"}"
  printf '%s' "$v"
}

unquote_env() {
  local v
  v="$(trim "$1")"
  v="${v%$'\r'}"
  if [[ "$v" == \"*\" && "$v" == *\" ]]; then
    v="${v:1:${#v}-2}"
    v="${v//\\\"/\"}"
    v="${v//\\\\/\\}"
  elif [[ "$v" == \'*\' && "$v" == *\' ]]; then
    v="${v:1:${#v}-2}"
    v="${v//\\\'/\'}"
  fi
  printf '%s' "$v"
}

read_env_file() {
  local file="$1" line key value
  [[ -f "$file" ]] || return 0

  while IFS= read -r line || [[ -n "$line" ]]; do
    line="${line%$'\r'}"
    [[ -z "$(trim "$line")" ]] && continue
    [[ "$(trim "$line")" == \#* ]] && continue
    [[ "$line" == export[[:space:]]* ]] && line="${line#export }"
    [[ "$line" == *=* ]] || continue

    key="$(trim "${line%%=*}")"
    [[ "$key" =~ ^[A-Za-z_][A-Za-z0-9_]*$ ]] || continue
    value="${line#*=}"
    MAIN_ENV["$key"]="$(unquote_env "$value")"
  done < "$file"

  MAIN_ENV_FILES+=("$file")
}

dotenv_get() {
  local file="$1" target="$2" line key value
  [[ -f "$file" ]] || return 1

  while IFS= read -r line || [[ -n "$line" ]]; do
    line="${line%$'\r'}"
    [[ -z "$(trim "$line")" ]] && continue
    [[ "$(trim "$line")" == \#* ]] && continue
    [[ "$line" == export[[:space:]]* ]] && line="${line#export }"
    [[ "$line" == *=* ]] || continue

    key="$(trim "${line%%=*}")"
    [[ "$key" == "$target" ]] || continue
    value="${line#*=}"
    unquote_env "$value"
    return 0
  done < "$file"

  return 1
}

cfg() {
  local key="$1" fallback="${2:-}" env_value=""
  env_value="$(printenv "$key" 2>/dev/null || true)"
  if [[ -n "$env_value" ]]; then printf '%s' "$env_value"; return 0; fi
  if [[ ${MAIN_ENV[$key]+_} ]]; then printf '%s' "${MAIN_ENV[$key]}"; return 0; fi
  printf '%s' "$fallback"
}

cfg_keep() {
  local key="$1" file="$2" fallback="${3:-}" env_value="" old=""
  env_value="$(printenv "$key" 2>/dev/null || true)"
  if [[ -n "$env_value" ]]; then printf '%s' "$env_value"; return 0; fi
  if [[ ${MAIN_ENV[$key]+_} && -n "${MAIN_ENV[$key]}" ]]; then printf '%s' "${MAIN_ENV[$key]}"; return 0; fi
  old="$(dotenv_get "$file" "$key" 2>/dev/null || true)"
  if [[ -n "$old" ]]; then printf '%s' "$old"; return 0; fi
  printf '%s' "$fallback"
}

is_weak_service_secret() {
  local value="${1:-}"
  [[ -z "$value" || "$value" == *CHANGE_ME* || "$value" == dev-* || ${#value} -lt 32 ]]
}

backup_main_env_once() {
  local file="$1"
  [[ -f "$file" ]] || return 0
  [[ "$MAIN_ENV_BACKUP_FILE" == "$file" ]] && return 0
  cp "$file" "${file}.bak.${STAMP}"
  MAIN_ENV_BACKUP_FILE="$file"
  log "backup main config: ${file#${ROOT_DIR}/} -> ${file#${ROOT_DIR}/}.bak.${STAMP}" >&2
}

dotenv_upsert_raw() {
  local file="$1" key="$2" value="$3"
  local tmp="${file}.tmp.${STAMP}.${key}"
  mkdir -p "$(dirname "$file")"
  if [[ -f "$file" ]]; then
    awk -v key="$key" -v value="$value" '
      $0 ~ "^[[:space:]]*(export[[:space:]]+)?" key "[[:space:]]*=" {
        print key "=" value
        done = 1
        next
      }
      { print }
      END {
        if (!done) print key "=" value
      }
    ' "$file" > "$tmp"
  else
    printf '%s=%s\n' "$key" "$value" > "$tmp"
  fi
  mv "$tmp" "$file"
  MAIN_ENV["$key"]="$value"
}

ensure_main_service_secret() {
  local key="$1" current="${2:-}" env_value="" file="" generated=""
  env_value="$(printenv "$key" 2>/dev/null || true)"
  if [[ -n "$env_value" ]]; then
    printf '%s' "$env_value"
    return 0
  fi
  if ! is_weak_service_secret "$current"; then
    printf '%s' "$current"
    return 0
  fi

  file="${MAIN_ENV_FILES[0]}"
  generated="s2s_$(rand_hex 32)"
  backup_main_env_once "$file"
  dotenv_upsert_raw "$file" "$key" "$generated"
  log "generated ${key} and wrote it to ${file#${ROOT_DIR}/}" >&2
  printf '%s' "$generated"
}

rand_hex() {
  openssl rand -hex "${1:-32}"
}

urlencode() {
  local s="$1" out="" i c hex
  LC_ALL=C
  for ((i = 0; i < ${#s}; i++)); do
    c="${s:i:1}"
    case "$c" in
      [a-zA-Z0-9.~_-]) out+="$c" ;;
      *) printf -v hex '%%%02X' "'$c"; out+="$hex" ;;
    esac
  done
  printf '%s' "$out"
}

env_quote() {
  local v="${1:-}"
  v="${v%$'\r'}"
  if [[ "$v" == *"'"* ]]; then
    v="${v//\\/\\\\}"
    v="${v//\"/\\\"}"
    v="${v//\$/\$\$}"
    printf '"%s"' "$v"
    return 0
  fi

  v="${v//\\/\\\\}"
  printf "'%s'" "$v"
}

env_line() {
  printf '%s=%s\n' "$1" "$(env_quote "${2:-}")"
}

write_env() {
  local file="$1"
  local tmp="${file}.tmp.${STAMP}"
  mkdir -p "$(dirname "$file")"
  cat > "$tmp"
  if [[ -f "$file" ]]; then
    cp "$file" "${file}.bak.${STAMP}"
    log "backup ${file#${ROOT_DIR}/} -> ${file#${ROOT_DIR}/}.bak.${STAMP}"
  fi
  mv "$tmp" "$file"
}

sql_quote() {
  local v="$1"
  v="$(printf '%s' "$v" | sed -e 's/\\/\\\\/g' -e "s/'/''/g")"
  printf "'%s'" "$v"
}

sql_ident() {
  local v="$1"
  v="$(printf '%s' "$v" | sed 's/`/``/g')"
  printf '`%s`' "$v"
}

mysql_exec() {
  MYSQL_PWD="$MYSQL_PASSWORD" mysql \
    --protocol=tcp \
    -h "$MYSQL_HOST" \
    -P "$MYSQL_PORT" \
    -u "$MYSQL_USER" \
    --connect-timeout=8 \
    --default-character-set=utf8mb4 \
    "$@"
}

mysql_scalar() {
  local database="$1" sql="$2"
  MYSQL_PWD="$MYSQL_PASSWORD" mysql \
    --protocol=tcp \
    -h "$MYSQL_HOST" \
    -P "$MYSQL_PORT" \
    -u "$MYSQL_USER" \
    --connect-timeout=8 \
    --default-character-set=utf8mb4 \
    --batch \
    --skip-column-names \
    "$database" \
    -e "$sql"
}

load_main_env() {
  if [[ -n "${MAIN_ENV_FILE:-}" ]]; then
    local file="$MAIN_ENV_FILE"
    [[ "$file" == /* ]] || file="${ROOT_DIR}/${file}"
    [[ -f "$file" ]] || die "MAIN_ENV_FILE does not exist: $file"
    read_env_file "$file"
  else
    read_env_file "${ROOT_DIR}/.env"
    read_env_file "${ROOT_DIR}/.env.local"
    read_env_file "${ROOT_DIR}/.env.production"
  fi

  [[ ${#MAIN_ENV_FILES[@]} -gt 0 ]] || die "No main-site env file found"
  log "loaded main config: ${MAIN_ENV_FILES[*]#${ROOT_DIR}/}"
}

parse_jdbc_mysql() {
  local jdbc_url="$1" rest authority_and_db authority
  [[ "$jdbc_url" == jdbc:mysql://* ]] || die "DB_URL is not a jdbc:mysql URL"

  rest="${jdbc_url#jdbc:mysql://}"
  authority_and_db="${rest%%\?*}"
  authority="${authority_and_db%%/*}"
  MAIN_DB_NAME="${authority_and_db#*/}"

  if [[ "$authority" == *:* ]]; then
    MYSQL_HOST="${authority%:*}"
    MYSQL_PORT="${authority##*:}"
  else
    MYSQL_HOST="$authority"
    MYSQL_PORT="3306"
  fi

  [[ -n "$MYSQL_HOST" && -n "$MYSQL_PORT" && -n "$MAIN_DB_NAME" && "$MAIN_DB_NAME" != "$authority_and_db" ]] \
    || die "Cannot parse MySQL host/port/db from DB_URL"
}

ensure_mysql() {
  if is_true "${SKIP_DB:-false}"; then
    warn "SKIP_DB=true, skip database and SSO repair"
    return 0
  fi

  need mysql
  need sed

  log "checking MySQL: ${MYSQL_HOST}:${MYSQL_PORT}/${MAIN_DB_NAME}"
  mysql_exec -e "SELECT 1" >/dev/null

  local main_exists
  main_exists="$(mysql_scalar information_schema "SELECT COUNT(*) FROM SCHEMATA WHERE SCHEMA_NAME = $(sql_quote "$MAIN_DB_NAME");")"
  [[ "$main_exists" == "1" ]] || die "Main database not found: $MAIN_DB_NAME"

  mysql_exec <<SQL
CREATE DATABASE IF NOT EXISTS \`zens_shop\`
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

USE $(sql_ident "$MAIN_DB_NAME");

CREATE TABLE IF NOT EXISTS \`sys_sso_client\` (
  \`id\` varchar(64) NOT NULL,
  \`client_id\` varchar(100) NOT NULL COMMENT '应用标识（唯一）',
  \`client_name\` varchar(200) NOT NULL COMMENT '应用名称',
  \`client_secret\` varchar(200) NOT NULL COMMENT '应用密钥',
  \`redirect_uri\` text NOT NULL COMMENT '回调地址',
  \`description\` varchar(500) DEFAULT NULL COMMENT '应用描述',
  \`logo_url\` varchar(500) DEFAULT NULL COMMENT '应用Logo URL',
  \`enabled\` tinyint(1) DEFAULT 1 COMMENT '是否启用 1:是 0:否',
  \`trusted\` tinyint(1) NOT NULL DEFAULT 1 COMMENT '第一方可信:1 自动授权跳过同意页,0 需手动同意',
  \`create_time\` datetime DEFAULT CURRENT_TIMESTAMP,
  \`update_time\` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (\`id\`),
  UNIQUE KEY \`uk_client_id\` (\`client_id\`),
  KEY \`idx_sso_client_enabled\` (\`enabled\`, \`create_time\`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='SSO 应用注册表';

SET @trusted_count := (
  SELECT COUNT(*)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'sys_sso_client'
    AND COLUMN_NAME = 'trusted'
);
SET @trusted_sql := IF(
  @trusted_count = 0,
  'ALTER TABLE \`sys_sso_client\` ADD COLUMN \`trusted\` tinyint(1) NOT NULL DEFAULT 0 COMMENT ''第一方可信:1 自动授权跳过同意页,0 需手动同意'' AFTER \`enabled\`',
  'SELECT 1'
);
PREPARE trusted_stmt FROM @trusted_sql;
EXECUTE trusted_stmt;
DEALLOCATE PREPARE trusted_stmt;

SET @redirect_type := (
  SELECT DATA_TYPE
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'sys_sso_client'
    AND COLUMN_NAME = 'redirect_uri'
  LIMIT 1
);
SET @redirect_sql := IF(
  @redirect_type IN ('varchar', 'char'),
  'ALTER TABLE \`sys_sso_client\` MODIFY COLUMN \`redirect_uri\` text NOT NULL COMMENT ''回调地址''',
  'SELECT 1'
);
PREPARE redirect_stmt FROM @redirect_sql;
EXECUTE redirect_stmt;
DEALLOCATE PREPARE redirect_stmt;

SET @post_type_count := (
  SELECT COUNT(*)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'sys_post'
    AND COLUMN_NAME = 'post_type'
);
SET @post_type_sql := IF(
  @post_type_count = 0,
  'ALTER TABLE `sys_post` ADD COLUMN `post_type` varchar(32) NOT NULL DEFAULT ''NORMAL'' COMMENT ''帖子类型 NORMAL/LOTTERY'' AFTER `is_anonymous`',
  'SELECT 1'
);
PREPARE post_type_stmt FROM @post_type_sql;
EXECUTE post_type_stmt;
DEALLOCATE PREPARE post_type_stmt;

SET @comment_deadline_count := (
  SELECT COUNT(*)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'sys_post'
    AND COLUMN_NAME = 'comment_deadline'
);
SET @comment_deadline_sql := IF(
  @comment_deadline_count = 0,
  'ALTER TABLE `sys_post` ADD COLUMN `comment_deadline` datetime DEFAULT NULL COMMENT ''抽奖帖评论截止时间'' AFTER `post_type`',
  'SELECT 1'
);
PREPARE comment_deadline_stmt FROM @comment_deadline_sql;
EXECUTE comment_deadline_stmt;
DEALLOCATE PREPARE comment_deadline_stmt;

SET @comment_once_count := (
  SELECT COUNT(*)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'sys_post'
    AND COLUMN_NAME = 'comment_once_per_user'
);
SET @comment_once_sql := IF(
  @comment_once_count = 0,
  'ALTER TABLE `sys_post` ADD COLUMN `comment_once_per_user` tinyint(1) NOT NULL DEFAULT 0 COMMENT ''抽奖帖是否限制每人评论一次'' AFTER `comment_deadline`',
  'SELECT 1'
);
PREPARE comment_once_stmt FROM @comment_once_sql;
EXECUTE comment_once_stmt;
DEALLOCATE PREPARE comment_once_stmt;

SET @location_name_count := (
  SELECT COUNT(*)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'sys_post'
    AND COLUMN_NAME = 'location_name'
);
SET @location_name_sql := IF(
  @location_name_count = 0,
  'ALTER TABLE `sys_post` ADD COLUMN `location_name` varchar(100) DEFAULT NULL COMMENT ''位置名称'' AFTER `comment_once_per_user`',
  'SELECT 1'
);
PREPARE location_name_stmt FROM @location_name_sql;
EXECUTE location_name_stmt;
DEALLOCATE PREPARE location_name_stmt;

SET @avg_dwell_sec_count := (
  SELECT COUNT(*)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'sys_post'
    AND COLUMN_NAME = 'avg_dwell_sec'
);
SET @avg_dwell_sec_sql := IF(
  @avg_dwell_sec_count = 0,
  'ALTER TABLE `sys_post` ADD COLUMN `avg_dwell_sec` int DEFAULT 0 COMMENT ''平均阅读时长(秒),热度加权用'' AFTER `heat_score`',
  'SELECT 1'
);
PREPARE avg_dwell_sec_stmt FROM @avg_dwell_sec_sql;
EXECUTE avg_dwell_sec_stmt;
DEALLOCATE PREPARE avg_dwell_sec_stmt;

SET @post_type_deadline_index_count := (
  SELECT COUNT(*)
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'sys_post'
    AND INDEX_NAME = 'idx_post_type_deadline'
);
SET @post_type_deadline_index_sql := IF(
  @post_type_deadline_index_count = 0,
  'ALTER TABLE `sys_post` ADD KEY `idx_post_type_deadline` (`post_type`, `comment_deadline`)',
  'SELECT 1'
);
PREPARE post_type_deadline_index_stmt FROM @post_type_deadline_index_sql;
EXECUTE post_type_deadline_index_stmt;
DEALLOCATE PREPARE post_type_deadline_index_stmt;
SQL

  log "database ready: zens_shop + ${MAIN_DB_NAME}.sys_sso_client + lottery post rules"
}

existing_sso_secret() {
  local client_id="$1"
  if is_true "${SKIP_DB:-false}"; then return 0; fi
  mysql_scalar "$MAIN_DB_NAME" "SELECT client_secret FROM sys_sso_client WHERE client_id = $(sql_quote "$client_id") LIMIT 1;" 2>/dev/null || true
}

upsert_sso_client() {
  local client_id="$1" client_name="$2" client_secret="$3" redirect_uri="$4" description="$5" logo_url="$6"
  if is_true "${SKIP_DB:-false}"; then return 0; fi

  mysql_exec "$MAIN_DB_NAME" <<SQL
INSERT INTO \`sys_sso_client\` (
  \`id\`, \`client_id\`, \`client_name\`, \`client_secret\`,
  \`redirect_uri\`, \`description\`, \`logo_url\`, \`enabled\`, \`trusted\`,
  \`create_time\`, \`update_time\`
) VALUES (
  REPLACE(UUID(), '-', ''),
  $(sql_quote "$client_id"),
  $(sql_quote "$client_name"),
  $(sql_quote "$client_secret"),
  $(sql_quote "$redirect_uri"),
  $(sql_quote "$description"),
  $(sql_quote "$logo_url"),
  1,
  1,
  NOW(),
  NOW()
)
ON DUPLICATE KEY UPDATE
  \`client_name\` = VALUES(\`client_name\`),
  \`client_secret\` = IF(\`client_secret\` IS NULL OR \`client_secret\` = '', VALUES(\`client_secret\`), \`client_secret\`),
  \`redirect_uri\` = VALUES(\`redirect_uri\`),
  \`description\` = VALUES(\`description\`),
  \`logo_url\` = VALUES(\`logo_url\`),
  \`enabled\` = 1,
  \`trusted\` = 1,
  \`update_time\` = NOW();
SQL
}

compose_up() {
  local dir="$1" name="$2"
  [[ -f "${dir}/docker-compose.yml" ]] || die "${name} missing docker-compose.yml"
  log "building and starting ${name}"
  (cd "$dir" && docker compose --env-file .env up -d --build --remove-orphans)
}

health_check() {
  local name="$1" url="$2" max="${3:-40}"
  if ! command -v curl >/dev/null 2>&1; then
    warn "curl not found, skip ${name} health check"
    return 0
  fi

  log "waiting for ${name}: ${url}"
  for ((i = 1; i <= max; i++)); do
    if curl -fsS --max-time 4 "$url" >/dev/null 2>&1; then
      log "${name} is ready"
      return 0
    fi
    sleep 2
  done

  warn "${name} health check failed; inspect container logs"
  return 1
}

main() {
  need docker
  need openssl
  docker compose version >/dev/null 2>&1 || die "Docker Compose v2 is required"

  [[ -d "$SHOP_DIR" ]] || die "missing zdc-shop directory"
  [[ -d "$LOTTERY_DIR" ]] || die "missing campus-lottery-station directory"
  [[ -d "$CDK_DIR" ]] || die "missing cdk-airdrop-station directory"

  load_main_env

  local main_domain main_site_url main_backend_url shop_domain lottery_domain cdk_domain
  main_domain="${MAIN_DOMAIN:-allinsong.top}"
  main_site_url="${MAIN_SITE_URL:-https://${main_domain}}"
  main_backend_url="${MAIN_SITE_BACKEND_URL:-${main_site_url}}"
  shop_domain="${SHOP_DOMAIN:-shop.${main_domain}}"
  lottery_domain="${LOTTERY_DOMAIN:-lottery.${main_domain}}"
  cdk_domain="${CDK_DOMAIN:-cdk.${main_domain}}"

  local shop_url lottery_url cdk_url logo_url
  shop_url="${SHOP_PUBLIC_URL:-https://${shop_domain}}"
  lottery_url="${LOTTERY_PUBLIC_URL:-https://${lottery_domain}}"
  cdk_url="${CDK_PUBLIC_URL:-https://${cdk_domain}}"
  logo_url="${MAIN_SITE_LOGO_URL:-${main_site_url}/logo.png}"

  local db_url jwt_secret shop_service_secret lottery_service_secret
  db_url="$(cfg DB_URL 'jdbc:mysql://127.0.0.1:3306/campus_pulse?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true')"
  MYSQL_USER="$(cfg DB_USERNAME root)"
  MYSQL_PASSWORD="$(cfg DB_PASSWORD '')"
  jwt_secret="$(cfg JWT_SECRET '')"
  shop_service_secret="$(cfg SHOP_SERVICE_SECRET 'dev-shop-service-secret-CHANGE_ME_at_least_16_chars')"
  lottery_service_secret="$(cfg LOTTERY_SERVICE_SECRET 'dev-lottery-service-secret-CHANGE_ME_at_least_16_chars')"

  [[ -n "$jwt_secret" ]] || die "JWT_SECRET is required for SSO"
  [[ ${#jwt_secret} -ge 32 ]] || warn "JWT_SECRET is short; production should use at least 32 chars"
  shop_service_secret="$(ensure_main_service_secret SHOP_SERVICE_SECRET "$shop_service_secret")"
  lottery_service_secret="$(ensure_main_service_secret LOTTERY_SERVICE_SECRET "$lottery_service_secret")"
  [[ "$shop_service_secret" != *CHANGE_ME* && "$shop_service_secret" != dev-* ]] || warn "SHOP_SERVICE_SECRET looks like a dev value"
  [[ "$lottery_service_secret" != *CHANGE_ME* && "$lottery_service_secret" != dev-* ]] || warn "LOTTERY_SERVICE_SECRET looks like a dev value"

  parse_jdbc_mysql "$db_url"
  ensure_mysql

  local shop_sso_secret lottery_sso_secret cdk_sso_secret
  shop_sso_secret="$(existing_sso_secret zdc-shop)"
  lottery_sso_secret="$(existing_sso_secret campus-lottery-station)"
  cdk_sso_secret="$(existing_sso_secret cdk-airdrop)"
  [[ -n "$shop_sso_secret" ]] || shop_sso_secret="sso_$(rand_hex 32)"
  [[ -n "$lottery_sso_secret" ]] || lottery_sso_secret="sso_$(rand_hex 32)"
  [[ -n "$cdk_sso_secret" ]] || cdk_sso_secret="sso_$(rand_hex 32)"

  local shop_redirects lottery_redirects cdk_redirects
  shop_redirects="${shop_url}/login/callback"
  lottery_redirects="${lottery_url}/api/auth/sso/callback"
  cdk_redirects="${cdk_url}/login/callback"

  [[ -n "${SHOP_EXTRA_REDIRECT_URIS:-}" ]] && shop_redirects="${shop_redirects}, ${SHOP_EXTRA_REDIRECT_URIS}"
  [[ -n "${LOTTERY_EXTRA_REDIRECT_URIS:-}" ]] && lottery_redirects="${lottery_redirects}, ${LOTTERY_EXTRA_REDIRECT_URIS}"
  [[ -n "${CDK_EXTRA_REDIRECT_URIS:-}" ]] && cdk_redirects="${cdk_redirects}, ${CDK_EXTRA_REDIRECT_URIS}"

  if is_true "${INCLUDE_LOCAL_REDIRECTS:-true}"; then
    shop_redirects="${shop_redirects}, http://localhost:3000/login/callback, http://127.0.0.1:3000/login/callback, http://localhost:3001/login/callback, http://127.0.0.1:3001/login/callback"
    lottery_redirects="${lottery_redirects}, http://localhost:8093/api/auth/sso/callback, http://127.0.0.1:8093/api/auth/sso/callback"
    cdk_redirects="${cdk_redirects}, http://localhost:8088/login/callback, http://127.0.0.1:8088/login/callback"
  fi

  upsert_sso_client zdc-shop 'Zens 积分商城' "$shop_sso_secret" "$shop_redirects" 'Zens 社区积分商城,使用主站账号单点登录并同步积分权益。' "$logo_url"
  upsert_sso_client campus-lottery-station 'Zens 抽奖站' "$lottery_sso_secret" "$lottery_redirects" 'Zens 社区抽奖子站,读取主站帖子评论并回流开奖结果。' "$logo_url"
  upsert_sso_client cdk-airdrop 'Zens CDK 空投站' "$cdk_sso_secret" "$cdk_redirects" 'Zens 社区 CDK 空投与活动兑换入口。' "$logo_url"
  log "SSO clients ready: zdc-shop / campus-lottery-station / cdk-airdrop"

  local docker_mysql_host db_user_encoded db_pass_encoded shop_database_url
  docker_mysql_host="${DOCKER_MYSQL_HOST:-host.docker.internal}"
  db_user_encoded="$(urlencode "$MYSQL_USER")"
  db_pass_encoded="$(urlencode "$MYSQL_PASSWORD")"
  shop_database_url="mysql://${db_user_encoded}:${db_pass_encoded}@${docker_mysql_host}:${MYSQL_PORT}/zens_shop"

  local shop_port lottery_port cdk_port
  shop_port="${SHOP_PORT:-3000}"
  lottery_port="${LOTTERY_PORT:-8093}"
  cdk_port="${CDK_AIRDROP_PORT:-8088}"

  local shop_session lottery_session cdk_pg_password
  shop_session="$(cfg_keep SESSION_PASSWORD "${SHOP_DIR}/.env" "$(rand_hex 32)")"
  lottery_session="$(cfg_keep LOTTERY_SESSION_SECRET "${LOTTERY_DIR}/.env" "$(rand_hex 32)")"
  cdk_pg_password="$(cfg_keep POSTGRES_PASSWORD "${CDK_DIR}/.env" "$(rand_hex 24)")"

  local r2_account r2_key r2_secret r2_bucket r2_public
  r2_account="$(cfg R2_ACCOUNT_ID '')"
  r2_key="$(cfg R2_ACCESS_KEY_ID '')"
  r2_secret="$(cfg R2_SECRET_ACCESS_KEY '')"
  r2_bucket="${SHOP_R2_BUCKET:-$(cfg R2_BUCKET 'zdc-shop-assets')}"
  r2_public="${SHOP_R2_PUBLIC_BASE_URL:-$(cfg R2_PUBLIC_BASE_URL "$main_site_url")}"
  [[ -n "$r2_account" && -n "$r2_key" && -n "$r2_secret" ]] || warn "R2 config is incomplete; shop image upload may be unavailable"

  local bot_token bot_username bot_password
  bot_token="$(cfg_keep LOTTERY_BOT_ACCESS_TOKEN "${LOTTERY_DIR}/.env" '')"
  bot_username="$(cfg_keep LOTTERY_BOT_USERNAME "${LOTTERY_DIR}/.env" 'zens-lottery-bot')"
  bot_password="$(cfg_keep LOTTERY_BOT_PASSWORD "${LOTTERY_DIR}/.env" '')"
  [[ -n "$bot_token" || -n "$bot_password" ]] || warn "Lottery bot credential is empty; draw result comments will not be posted automatically"

  write_env "${SHOP_DIR}/.env" <<EOF
# Generated by deploy-subsites.sh at ${STAMP}
$(env_line DATABASE_URL "$shop_database_url")
$(env_line JWT_SECRET "$jwt_secret")
$(env_line SHOP_SERVICE_SECRET "$shop_service_secret")
$(env_line SHOP_SERVICE_ID zdc-shop)
$(env_line MAIN_SITE_BACKEND_URL "$main_backend_url")
$(env_line NEXT_PUBLIC_COMMUNITY_URL "$main_site_url")
$(env_line NEXT_PUBLIC_SSO_CLIENT_ID zdc-shop)
$(env_line SSO_CLIENT_SECRET "$shop_sso_secret")
$(env_line SESSION_PASSWORD "$shop_session")
$(env_line SESSION_COOKIE_NAME zs_session)
$(env_line SHOP_BIND "${SHOP_BIND:-127.0.0.1}")
$(env_line SHOP_PORT "$shop_port")
$(env_line NEXT_PUBLIC_SITE_NAME 'Zens · 积分商城')
$(env_line NEXT_PUBLIC_SITE_URL "$shop_url")
$(env_line R2_ACCOUNT_ID "$r2_account")
$(env_line R2_ACCESS_KEY_ID "$r2_key")
$(env_line R2_SECRET_ACCESS_KEY "$r2_secret")
$(env_line R2_BUCKET "$r2_bucket")
$(env_line R2_PUBLIC_BASE_URL "$r2_public")
EOF

  write_env "${LOTTERY_DIR}/.env" <<EOF
# Generated by deploy-subsites.sh at ${STAMP}
$(env_line LOTTERY_ADDR ':8093')
$(env_line LOTTERY_BIND "${LOTTERY_BIND:-127.0.0.1}")
$(env_line LOTTERY_PORT "$lottery_port")
$(env_line LOTTERY_PUBLIC_URL "$lottery_url")
$(env_line LOTTERY_LOGO_URL "$logo_url")
$(env_line LOTTERY_DATA '/app/data/state.json')
$(env_line LOTTERY_SESSION_SECRET "$lottery_session")
$(env_line LOTTERY_ALLOW_DEMO_SSO_FALLBACK false)
$(env_line LOTTERY_COMMENT_MAX_PAGES "${LOTTERY_COMMENT_MAX_PAGES:-50}")
$(env_line LOTTERY_SERVICE_ID campus-lottery-station)
$(env_line LOTTERY_SERVICE_SECRET "$lottery_service_secret")
$(env_line COMMUNITY_BASE_URL "$main_site_url")
$(env_line COMMUNITY_API_BASE_URL "$main_backend_url")
$(env_line COMMUNITY_SSO_AUTHORIZE_URL "${main_site_url}/sso/authorize")
$(env_line COMMUNITY_SSO_TOKEN_URL '')
$(env_line COMMUNITY_JWT_SECRET "$jwt_secret")
$(env_line SSO_CLIENT_ID campus-lottery-station)
$(env_line SSO_CLIENT_SECRET "$lottery_sso_secret")
$(env_line LOTTERY_BOT_ACCESS_TOKEN "$bot_token")
$(env_line LOTTERY_BOT_USERNAME "$bot_username")
$(env_line LOTTERY_BOT_PASSWORD "$bot_password")
EOF

  local hcaptcha_secret hcaptcha_site_key cdk_pg_dsn
  hcaptcha_secret="$(cfg_keep HCAPTCHA_SECRET "${CDK_DIR}/.env" '0x0000000000000000000000000000000000000000')"
  hcaptcha_site_key="$(cfg_keep HCAPTCHA_SITE_KEY "${CDK_DIR}/.env" '10000000-ffff-ffff-ffff-000000000001')"
  cdk_pg_dsn="postgres://cdk_airdrop:$(urlencode "$cdk_pg_password")@postgres:5432/cdk_airdrop?sslmode=disable"

  write_env "${CDK_DIR}/.env" <<EOF
# Generated by deploy-subsites.sh at ${STAMP}
$(env_line CDK_AIRDROP_BIND "${CDK_AIRDROP_BIND:-127.0.0.1}")
$(env_line CDK_AIRDROP_PORT "$cdk_port")
$(env_line CDK_PUBLIC_URL "$cdk_url")
$(env_line HCAPTCHA_SECRET "$hcaptcha_secret")
$(env_line HCAPTCHA_VERIFY_URL "${HCAPTCHA_VERIFY_URL:-https://api.hcaptcha.com/siteverify}")
$(env_line HCAPTCHA_SITE_KEY "$hcaptcha_site_key")
$(env_line VITE_HCAPTCHA_SITE_KEY "${VITE_HCAPTCHA_SITE_KEY:-$hcaptcha_site_key}")
$(env_line POSTGRES_DB cdk_airdrop)
$(env_line POSTGRES_USER cdk_airdrop)
$(env_line POSTGRES_PASSWORD "$cdk_pg_password")
$(env_line CDK_POSTGRES_DSN "$cdk_pg_dsn")
$(env_line CDK_MYSQL_DSN '')
$(env_line CDK_AIRDROP_REDIS_URL "${CDK_AIRDROP_REDIS_URL:-}")
$(env_line CDK_AIRDROP_RABBITMQ_URL "${CDK_AIRDROP_RABBITMQ_URL:-}")
$(env_line CDK_COMMUNITY_URL "$main_site_url")
$(env_line CDK_COMMUNITY_CLIENT_ID cdk-airdrop)
$(env_line CDK_COMMUNITY_JWT_SECRET "$jwt_secret")
$(env_line CDK_AIRDROP_ALLOWED_ORIGINS "${cdk_url},${main_site_url}")
$(env_line CDK_AIRDROP_CSP_ENABLED true)
$(env_line CDK_AIRDROP_HSTS_ENABLED true)
$(env_line CDK_AIRDROP_CSP_CONNECT_SRC "'self' https://hcaptcha.com https://*.hcaptcha.com ${main_site_url} ${main_backend_url}")
$(env_line CDK_AIRDROP_CSP_IMG_SRC "'self' data: ${main_site_url} ${r2_public}")
EOF

  compose_up "$SHOP_DIR" "Zens Shop"
  compose_up "$LOTTERY_DIR" "Zens Lottery"
  compose_up "$CDK_DIR" "Zens CDK"

  if is_true "${RUN_SHOP_SEED:-false}"; then
    log "RUN_SHOP_SEED=true, running shop seed"
    (cd "$SHOP_DIR" && docker compose --env-file .env exec -T zdc-shop npm run db:seed) || warn "shop seed failed"
  fi

  local failed=0
  health_check "Zens Shop" "http://127.0.0.1:${shop_port}/api/auth/community-config" || failed=1
  health_check "Zens Lottery" "http://127.0.0.1:${lottery_port}/api/health" || failed=1
  health_check "Zens CDK" "http://127.0.0.1:${cdk_port}/health" || failed=1

  printf '\n'
  log "sub-sites deployment finished"
  printf '  Shop:    %s -> http://127.0.0.1:%s\n' "$shop_url" "$shop_port"
  printf '  Lottery: %s -> http://127.0.0.1:%s\n' "$lottery_url" "$lottery_port"
  printf '  CDK:     %s -> http://127.0.0.1:%s\n' "$cdk_url" "$cdk_port"
  printf '\nNginx/BT reverse proxy should point each subdomain to the matching 127.0.0.1 port above.\n'
  printf 'The main site was not restarted.\n'

  [[ "$failed" == "0" ]] || exit 2
}

main "$@"
