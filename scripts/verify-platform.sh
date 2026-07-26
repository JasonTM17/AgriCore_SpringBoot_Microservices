#!/usr/bin/env bash
# Unix counterpart of verify-platform.ps1
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
EVIDENCE_DIR="${EVIDENCE_DIR:-${1:-./.verify-evidence}}"
mkdir -p "$EVIDENCE_DIR"
cd "$ROOT"

if [[ ! -f .env && -f .env.example ]]; then
  cp .env.example .env
fi

JWT_DIR="$ROOT/infrastructure/jwt"
JWT_PRIVATE_KEY="$JWT_DIR/private.pem"
JWT_PUBLIC_KEY="$JWT_DIR/public.pem"
if [[ ! -f "$JWT_PRIVATE_KEY" || ! -f "$JWT_PUBLIC_KEY" ]]; then
  if ! command -v openssl >/dev/null 2>&1; then
    echo "openssl is required to generate local JWT signing keys" >&2
    exit 1
  fi
  echo "== generate local JWT signing keys =="
  mkdir -p "$JWT_DIR"
  openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out "$JWT_PRIVATE_KEY"
  openssl pkey -in "$JWT_PRIVATE_KEY" -pubout -out "$JWT_PUBLIC_KEY"
fi

echo "== docker compose up (full stack) =="
docker compose -f docker-compose.yml up -d --build

wait_up() {
  local url="$1" timeout="${2:-600}"
  local end=$((SECONDS + timeout))
  while (( SECONDS < end )); do
    if curl -fsS "$url" >/dev/null 2>&1; then
      echo "UP $url"
      return 0
    fi
    sleep 5
  done
  echo "Timeout waiting for $url" >&2
  return 1
}

echo "== wait health =="
for u in \
  http://localhost:8081/actuator/health \
  http://localhost:8082/actuator/health \
  http://localhost:8086/actuator/health \
  http://localhost:8087/actuator/health \
  http://localhost:8092/actuator/health \
  http://localhost:8080/actuator/health
do
  wait_up "$u" 600
done

docker compose -f docker-compose.yml ps >"$EVIDENCE_DIR/compose-ps.txt"
git log --oneline -40 >"$EVIDENCE_DIR/git-log.txt"
MVN_LOG="$EVIDENCE_DIR/mvn-test.log"
./mvnw test -DskipITs | tee "$MVN_LOG"

EXPECTED_PG_SUMMARY="Tests run: 3, Failures: 0, Errors: 0, Skipped: 0"
PG_LINE="$(grep "InventoryPostgresIdempotencyTest" "$MVN_LOG" | tail -n 1 || true)"
if [[ "$PG_LINE" != *"$EXPECTED_PG_SUMMARY"* ]]; then
  echo "InventoryPostgresIdempotencyTest must show '$EXPECTED_PG_SUMMARY' (got: $PG_LINE)" >&2
  exit 1
fi
echo "InventoryPostgresIdempotencyTest: $PG_LINE"

# PowerShell e2e preferred on Windows; on Unix run via pwsh if available
if command -v pwsh >/dev/null 2>&1; then
  pwsh -File scripts/e2e-happy-path.ps1 -EvidenceDir "$EVIDENCE_DIR"
else
  echo "pwsh not found — install PowerShell Core or run e2e-happy-path.ps1 manually" >&2
  exit 1
fi

grep -q farmName "$EVIDENCE_DIR/traceability.json"
grep -q plotCode "$EVIDENCE_DIR/traceability.json"
echo "VERIFY PLATFORM OK — evidence in $EVIDENCE_DIR"
