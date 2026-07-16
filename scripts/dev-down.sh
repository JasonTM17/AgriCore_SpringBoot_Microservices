#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
docker compose down || true
docker compose -f docker-compose.infrastructure.yml down || true
echo "AgriCore containers stopped."
