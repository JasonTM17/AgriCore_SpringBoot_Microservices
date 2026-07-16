#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
if [[ ! -f .env ]]; then
  cp .env.example .env
  echo "Created .env from .env.example"
fi
docker compose -f docker-compose.infrastructure.yml up -d
echo "Infrastructure starting. Kafka UI: http://localhost:8088"
