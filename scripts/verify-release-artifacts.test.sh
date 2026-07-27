#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
VERIFIER="$ROOT/scripts/verify-release-artifacts.sh"
TMP_ROOT="$(mktemp -d)"
trap 'rm -rf "$TMP_ROOT"' EXIT

FAKE_BIN="$TMP_ROOT/bin"
mkdir -p "$FAKE_BIN"

cat >"$FAKE_BIN/docker" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
count_file="$FAKE_STATE/docker-count"
count="$(cat "$count_file" 2>/dev/null || echo 0)"
count=$((count + 1))
printf '%s\n' "$count" >"$count_file"

if (( count <= ${FAKE_DOCKER_FAILURES:-0} )); then
  echo "simulated registry exit 255" >&2
  exit 255
fi

reference="${!#}"
if [[ "${FAKE_MALFORMED_DIGEST:-0}" == "1" ]]; then
  printf 'Name: %s\nDigest: not-a-digest\n' "$reference"
  exit 0
fi

digest="$FAKE_EXPECTED_DIGEST"
if [[ "$reference" == ghcr.io/* && -n "${FAKE_GHCR_DIGEST:-}" ]]; then
  digest="$FAKE_GHCR_DIGEST"
fi
if [[ "$reference" == *":1234567" && -n "${FAKE_SHORT_DIGEST:-}" ]]; then
  digest="$FAKE_SHORT_DIGEST"
fi
printf 'Name: %s\nDigest: %s\n' "$reference" "$digest"
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
echo "simulated signature verification succeeded"
EOF

chmod +x "$FAKE_BIN/docker" "$FAKE_BIN/cosign"

EXPECTED_DIGEST="sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
MISMATCH_DIGEST="sha256:bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"

run_verifier() {
  env \
    PATH="$FAKE_BIN:$PATH" \
    PUBLISH_SHA="1234567890abcdef1234567890abcdef12345678" \
    DOCKERHUB_REPOSITORY="docker.io/example/agricore-identity" \
    GHCR_REPOSITORY="ghcr.io/example/agricore-identity" \
    EXPECTED_CERTIFICATE_IDENTITY="https://github.com/example/repo/.github/workflows/docker-publish.yml@refs/heads/main" \
    REGISTRY_RETRY_ATTEMPTS=3 \
    REGISTRY_RETRY_DELAY_SECONDS=0 \
    REGISTRY_RETRY_MAX_DELAY_SECONDS=0 \
    bash "$VERIFIER"
}

transient_state="$TMP_ROOT/transient"
mkdir -p "$transient_state"
transient_log="$TMP_ROOT/transient.log"
if ! FAKE_STATE="$transient_state" \
  FAKE_EXPECTED_DIGEST="$EXPECTED_DIGEST" \
  FAKE_DOCKER_FAILURES=1 \
  FAKE_COSIGN_FAILURES=1 \
  run_verifier >"$transient_log" 2>&1; then
  cat "$transient_log" >&2
  echo "Verifier did not recover from transient registry failures" >&2
  exit 1
fi

grep -q "Inspect .* failed on attempt 1/3, exit=255; retrying in 0s" "$transient_log"
grep -q "Verify signature .* failed on attempt 1/3, exit=255; retrying in 0s" "$transient_log"
[[ "$(cat "$transient_state/docker-count")" == "6" ]]
[[ "$(cat "$transient_state/cosign-count")" == "3" ]]

docker_exhausted_state="$TMP_ROOT/docker-exhausted"
mkdir -p "$docker_exhausted_state"
docker_exhausted_log="$TMP_ROOT/docker-exhausted.log"
if FAKE_STATE="$docker_exhausted_state" \
  FAKE_EXPECTED_DIGEST="$EXPECTED_DIGEST" \
  FAKE_DOCKER_FAILURES=99 \
  FAKE_COSIGN_FAILURES=0 \
  run_verifier >"$docker_exhausted_log" 2>&1; then
  echo "Verifier accepted a persistently unavailable registry" >&2
  exit 1
fi

grep -q "failed after 3 attempt(s), exit=255" "$docker_exhausted_log"
[[ "$(cat "$docker_exhausted_state/docker-count")" == "3" ]]

cosign_exhausted_state="$TMP_ROOT/cosign-exhausted"
mkdir -p "$cosign_exhausted_state"
cosign_exhausted_log="$TMP_ROOT/cosign-exhausted.log"
if FAKE_STATE="$cosign_exhausted_state" \
  FAKE_EXPECTED_DIGEST="$EXPECTED_DIGEST" \
  FAKE_DOCKER_FAILURES=0 \
  FAKE_COSIGN_FAILURES=99 \
  run_verifier >"$cosign_exhausted_log" 2>&1; then
  echo "Verifier accepted a persistently unavailable signature service" >&2
  exit 1
fi

grep -q "Verify signature .* failed after 3 attempt(s), exit=255" "$cosign_exhausted_log"
[[ "$(cat "$cosign_exhausted_state/cosign-count")" == "3" ]]

malformed_state="$TMP_ROOT/malformed"
mkdir -p "$malformed_state"
malformed_log="$TMP_ROOT/malformed.log"
if FAKE_STATE="$malformed_state" \
  FAKE_EXPECTED_DIGEST="$EXPECTED_DIGEST" \
  FAKE_MALFORMED_DIGEST=1 \
  FAKE_DOCKER_FAILURES=0 \
  FAKE_COSIGN_FAILURES=0 \
  run_verifier >"$malformed_log" 2>&1; then
  echo "Verifier accepted a malformed registry digest" >&2
  exit 1
fi

grep -q "Registry returned no valid digest" "$malformed_log"

short_mismatch_state="$TMP_ROOT/short-mismatch"
mkdir -p "$short_mismatch_state"
short_mismatch_log="$TMP_ROOT/short-mismatch.log"
if FAKE_STATE="$short_mismatch_state" \
  FAKE_EXPECTED_DIGEST="$EXPECTED_DIGEST" \
  FAKE_SHORT_DIGEST="$MISMATCH_DIGEST" \
  FAKE_DOCKER_FAILURES=0 \
  FAKE_COSIGN_FAILURES=0 \
  run_verifier >"$short_mismatch_log" 2>&1; then
  echo "Verifier accepted a short-tag digest mismatch" >&2
  exit 1
fi

grep -q "Release verification failed for docker.io/example/agricore-identity:1234567" "$short_mismatch_log"

mismatch_state="$TMP_ROOT/mismatch"
mkdir -p "$mismatch_state"
mismatch_log="$TMP_ROOT/mismatch.log"
if FAKE_STATE="$mismatch_state" \
  FAKE_EXPECTED_DIGEST="$EXPECTED_DIGEST" \
  FAKE_GHCR_DIGEST="$MISMATCH_DIGEST" \
  FAKE_DOCKER_FAILURES=0 \
  FAKE_COSIGN_FAILURES=0 \
  run_verifier >"$mismatch_log" 2>&1; then
  echo "Verifier accepted a cross-registry digest mismatch" >&2
  exit 1
fi

grep -q "Release verification failed for ghcr.io/example/agricore-identity" "$mismatch_log"
echo "Release verifier regression tests passed"
