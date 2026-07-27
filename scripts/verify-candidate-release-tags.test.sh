#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
VERIFIER="$ROOT/scripts/verify-candidate-release-tags.sh"
TMP_ROOT="$(mktemp -d)"
trap 'rm -rf "$TMP_ROOT"' EXIT

FAKE_BIN="$TMP_ROOT/bin"
mkdir -p "$FAKE_BIN"

cat >"$FAKE_BIN/docker" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
reference="${!#}"
safe_name="$(printf '%s' "$reference" | tr '/:@' '____')"
count_file="$FAKE_STATE/$safe_name.count"
count="$(cat "$count_file" 2>/dev/null || echo 0)"
count=$((count + 1))
printf '%s\n' "$count" >"$count_file"

if [[ "$reference" == "${FAKE_FAILURE_REFERENCE:-}" && "$count" -le "${FAKE_FAILURES:-0}" ]]; then
  echo "simulated registry exit 255" >&2
  exit 255
fi

if [[ "$reference" == *":1234567890abcdef1234567890abcdef12345678" || "$reference" == *":1234567" ]]; then
  if [[ -n "${FAKE_RELEASE_DIGEST:-}" ]]; then
    printf 'Name: %s\nDigest: %s\n' "$reference" "$FAKE_RELEASE_DIGEST"
    exit 0
  fi
  echo "manifest unknown: manifest unknown" >&2
  exit 1
fi

printf 'Name: %s\nDigest: %s\n' "$reference" "$FAKE_EXPECTED_DIGEST"
EOF
chmod +x "$FAKE_BIN/docker"

EXPECTED_DIGEST="sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
MISMATCH_DIGEST="sha256:bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
DOCKERHUB_FULL="docker.io/example/agricore-inventory:1234567890abcdef1234567890abcdef12345678"

run_verifier() {
  env \
    PATH="$FAKE_BIN:$PATH" \
    EXPECTED_DIGEST="$EXPECTED_DIGEST" \
    DOCKERHUB_CANDIDATE="docker.io/example/agricore-inventory:candidate" \
    GHCR_CANDIDATE="ghcr.io/example/agricore-inventory:candidate" \
    DOCKERHUB_FULL="$DOCKERHUB_FULL" \
    DOCKERHUB_SHORT="docker.io/example/agricore-inventory:1234567" \
    GHCR_FULL="ghcr.io/example/agricore-inventory:1234567890abcdef1234567890abcdef12345678" \
    GHCR_SHORT="ghcr.io/example/agricore-inventory:1234567" \
    REGISTRY_RETRY_ATTEMPTS=3 \
    REGISTRY_RETRY_DELAY_SECONDS=0 \
    REGISTRY_RETRY_MAX_DELAY_SECONDS=0 \
    bash "$VERIFIER"
}

absent_state="$TMP_ROOT/absent"
mkdir -p "$absent_state"
FAKE_STATE="$absent_state" \
  FAKE_EXPECTED_DIGEST="$EXPECTED_DIGEST" \
  run_verifier >/dev/null

collision_state="$TMP_ROOT/collision"
mkdir -p "$collision_state"
collision_log="$TMP_ROOT/collision.log"
if FAKE_STATE="$collision_state" \
  FAKE_EXPECTED_DIGEST="$EXPECTED_DIGEST" \
  FAKE_RELEASE_DIGEST="$MISMATCH_DIGEST" \
  FAKE_FAILURE_REFERENCE="$DOCKERHUB_FULL" \
  FAKE_FAILURES=1 \
  run_verifier >"$collision_log" 2>&1; then
  echo "Candidate verifier accepted an immutable tag collision" >&2
  exit 1
fi
grep -q "retrying in 0s" "$collision_log"
grep -q "Immutable SHA tag collision" "$collision_log"

unreadable_state="$TMP_ROOT/unreadable"
mkdir -p "$unreadable_state"
unreadable_log="$TMP_ROOT/unreadable.log"
if FAKE_STATE="$unreadable_state" \
  FAKE_EXPECTED_DIGEST="$EXPECTED_DIGEST" \
  FAKE_FAILURE_REFERENCE="$DOCKERHUB_FULL" \
  FAKE_FAILURES=99 \
  run_verifier >"$unreadable_log" 2>&1; then
  echo "Candidate verifier treated persistent registry failure as absence" >&2
  exit 1
fi
grep -q "failed after 3 attempt(s), exit=255" "$unreadable_log"
grep -q "Unable to establish immutable tag availability" "$unreadable_log"

candidate_retry_state="$TMP_ROOT/candidate-retry"
mkdir -p "$candidate_retry_state"
candidate_retry_log="$TMP_ROOT/candidate-retry.log"
FAKE_STATE="$candidate_retry_state" \
  FAKE_EXPECTED_DIGEST="$EXPECTED_DIGEST" \
  FAKE_FAILURE_REFERENCE="docker.io/example/agricore-inventory:candidate" \
  FAKE_FAILURES=1 \
  run_verifier >"$candidate_retry_log" 2>&1
grep -q "retrying in 0s" "$candidate_retry_log"

echo "Candidate release-tag regression tests passed"
