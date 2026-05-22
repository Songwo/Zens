#!/usr/bin/env sh
set -eu

cd "$(dirname "$0")/.."
mkdir -p logs tmp uploads data

if [ -z "${MEDIA_ADMIN_PASSWORD:-}" ]; then
  export MEDIA_ADMIN_PASSWORD="admin123456"
fi

echo "Starting go-media-service in dev mode..."
exec go run ./cmd/media-service
