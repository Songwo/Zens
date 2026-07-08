#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT_DIR"

if [ -f ".env.local" ]; then
  set -a
  . ./.env.local
  set +a
fi

python -m uvicorn app.main:app --host 0.0.0.0 --port "${AGENT_SERVICE_PORT:-7810}" --reload
