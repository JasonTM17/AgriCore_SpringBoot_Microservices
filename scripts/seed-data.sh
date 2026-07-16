#!/usr/bin/env bash
set -euo pipefail
FARM_BASE="${FARM_BASE:-http://localhost:8082}"
HDR=(-H "Content-Type: application/json" -H "X-Dev-User: seed" -H "X-Dev-Roles: FARM_MANAGER")
curl -sS -X POST "${FARM_BASE}/api/v1/farms" "${HDR[@]}" -d '{
  "code":"FARM-DL-01","name":"Nong trai Dak Lak","address":"Buon Ma Thuot",
  "province":"Dak Lak","totalAreaHa":120.5,"latitude":12.6667,"longitude":108.05
}'
echo
echo "Seed farm request sent. Use identity register for sample users."
