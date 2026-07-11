#!/usr/bin/env sh
set -eu

cd "$(dirname "$0")/.."
mkdir -p logs tmp uploads data

for name in MEDIA_UPLOAD_JWT_SECRET MEDIA_ADMIN_PASSWORD MEDIA_ADMIN_JWT_SECRET MEDIA_ADMIN_SERVICE_TOKENS; do
  eval "value=\${$name:-}"
  if [ -z "$value" ]; then
    echo "[ERROR] $name is required; export the values from a local, untracked env file first" >&2
    exit 1
  fi
done

echo "Starting go-media-service in dev mode..."
exec go run ./cmd/media-service
