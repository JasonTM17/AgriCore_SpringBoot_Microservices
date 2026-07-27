#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
PROMOTER="$ROOT/scripts/promote-release-artifacts.sh"
TMP_ROOT="$(mktemp -d)"
trap 'rm -rf "$TMP_ROOT"' EXIT

FAKE_BIN="$TMP_ROOT/bin"
mkdir -p "$FAKE_BIN"

cat >"$FAKE_BIN/docker" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail

if [[ "$1 $2 $3" == "buildx imagetools inspect" ]]; then
  count_file="$FAKE_STATE/inspect-count"
  count="$(cat "$count_file" 2>/dev/null || echo 0)"
  count=$((count + 1))
  printf '%s\n' "$count" >"$count_file"
  if (( count <= ${FAKE_INSPECT_FAILURES:-0} )); then
    echo "simulated registry exit 255" >&2
    exit 255
  fi
  reference="${!#}"
  digest="$FAKE_EXPECTED_DIGEST"
  if [[ "$reference" == ghcr.io/* && -n "${FAKE_GHCR_DIGEST:-}" ]]; then
    digest="$FAKE_GHCR_DIGEST"
  fi
  printf 'Name: %s\nDigest: %s\n' "$reference" "$digest"
  exit 0
fi

if [[ "$1 $2 $3" == "buildx imagetools create" ]]; then
  count_file="$FAKE_STATE/create-count"
  count="$(cat "$count_file" 2>/dev/null || echo 0)"
  count=$((count + 1))
  printf '%s\n' "$count" >"$count_file"
  if (( count <= ${FAKE_CREATE_FAILURES:-0} )); then
    echo "simulated promotion exit 255" >&2
    exit 255
  fi
  exit 0
fi

echo "unexpected docker invocation: $*" >&2
exit 2
EOF

cat >"$FAKE_BIN/cosign" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
count_file="$FAKE_STATE/cosign-count"
count="$(cat "$count_file" 2>/dev/null || echo 0)"
count=$((count + 1))
printf '%s\n' "$count" >"$count_file"
if (( count <= ${FAKE_COSIGN_FAILURES:-0} )); then
  echo "simulated cosign exit 255" >&2
  exit 255
fi
EOF

chmod +x "$FAKE_BIN/docker" "$FAKE_BIN/cosign"

EXPECTED_DIGEST="sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
MISMATCH_DIGEST="sha256:bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"

run_promoter() {
  env \
    PATH="$FAKE_BIN:$PATH" \
    PUBLISH_SHA="1234567890abcdef1234567890abcdef12345678" \
    CANDIDATE_TAG="candidate-1234567890abcdef1234567890abcdef12345678-42-1" \
    DOCKERHUB_REPOSITORY="docker.io/example/agricore-inventory" \
    GHCR_REPOSITORY="ghcr.io/example/agricore-inventory" \
    EXPECTED_CERTIFICATE_IDENTITY="https://github.com/example/repo/.github/workflows/docker-publish.yml@refs/heads/main" \
    REGISTRY_RETRY_ATTEMPTS=3 \
    REGISTRY_RETRY_DELAY_SECONDS=0 \
    REGISTRY_RETRY_MAX_DELAY_SECONDS=0 \
    bash "$PROMOTER"
}

transient_state="$TMP_ROOT/transient"
mkdir -p "$transient_state"
transient_log="$TMP_ROOT/transient.log"
if ! FAKE_STATE="$transient_state" \
  FAKE_EXPECTED_DIGEST="$EXPECTED_DIGEST" \
  FAKE_INSPECT_FAILURES=1 \
  FAKE_COSIGN_FAILURES=1 \
  FAKE_CREATE_FAILURES=1 \
  run_promoter >"$transient_log" 2>&1; then
  cat "$transient_log" >&2
  echo "Promoter did not recover from transient registry failures" >&2
  exit 1
fi

grep -q "Inspect .* failed on attempt 1/3, exit=255; retrying in 0s" "$transient_log"
grep -q "Verify signature .* failed on attempt 1/3, exit=255; retrying in 0s" "$transient_log"
grep -q "Promote Docker Hub release tags failed on attempt 1/3, exit=255; retrying in 0s" "$transient_log"
[[ "$(cat "$transient_state/inspect-count")" == "3" ]]
[[ "$(cat "$transient_state/cosign-count")" == "3" ]]
[[ "$(cat "$transient_state/create-count")" == "3" ]]

mismatch_state="$TMP_ROOT/mismatch"
mkdir -p "$mismatch_state"
mismatch_log="$TMP_ROOT/mismatch.log"
if FAKE_STATE="$mismatch_state" \
  FAKE_EXPECTED_DIGEST="$EXPECTED_DIGEST" \
  FAKE_GHCR_DIGEST="$MISMATCH_DIGEST" \
  FAKE_INSPECT_FAILURES=0 \
  FAKE_COSIGN_FAILURES=0 \
  FAKE_CREATE_FAILURES=0 \
  run_promoter >"$mismatch_log" 2>&1; then
  echo "Promoter accepted a cross-registry digest mismatch" >&2
  exit 1
fi
grep -q "Candidate parity failed" "$mismatch_log"
[[ ! -f "$mismatch_state/create-count" ]]

create_exhausted_state="$TMP_ROOT/create-exhausted"
mkdir -p "$create_exhausted_state"
create_exhausted_log="$TMP_ROOT/create-exhausted.log"
if FAKE_STATE="$create_exhausted_state" \
  FAKE_EXPECTED_DIGEST="$EXPECTED_DIGEST" \
  FAKE_INSPECT_FAILURES=0 \
  FAKE_COSIGN_FAILURES=0 \
  FAKE_CREATE_FAILURES=99 \
  run_promoter >"$create_exhausted_log" 2>&1; then
  echo "Promoter accepted persistent registry write failure" >&2
  exit 1
fi
grep -q "Promote Docker Hub release tags failed after 3 attempt(s), exit=255" "$create_exhausted_log"
[[ "$(cat "$create_exhausted_state/create-count")" == "3" ]]

echo "Release promoter regression tests passed"
