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
./mvnw test -DskipITs | tee "$EVIDENCE_DIR/mvn-test.log"

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
