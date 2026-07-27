#!/usr/bin/env bash
set -euo pipefail

require_env() {
  local name="$1"
  if [[ -z "${!name:-}" ]]; then
    echo "Required environment variable is missing: $name" >&2
    exit 2
  fi
}

for variable in \
  EXPECTED_DIGEST \
  DOCKERHUB_CANDIDATE \
  GHCR_CANDIDATE \
  DOCKERHUB_FULL \
  DOCKERHUB_SHORT \
  GHCR_FULL \
  GHCR_SHORT
do
  require_env "$variable"
done

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
# shellcheck source=registry-retry.sh
source "$SCRIPT_DIR/registry-retry.sh"
registry_retry_validate

require_digest() {
  local reference="$1"
  local actual

  if ! actual="$(registry_digest "$reference")"; then
    echo "Candidate tag is missing or unreadable: $reference" >&2
    exit 1
  fi
  if [[ "$actual" != "$EXPECTED_DIGEST" ]]; then
    echo "Digest mismatch for $reference: expected=$EXPECTED_DIGEST actual=$actual" >&2
    exit 1
  fi
}

require_immutable_or_absent() {
  local reference="$1"
  local existing
  local status

  if existing="$(registry_digest_or_absent "$reference")"; then
    if [[ "$existing" != "$EXPECTED_DIGEST" ]]; then
      echo "Immutable SHA tag collision for $reference: expected=$EXPECTED_DIGEST existing=$existing" >&2
      exit 1
    fi
    return 0
  else
    status=$?
  fi

  if (( status == 3 )); then
    return 0
  fi

  echo "Unable to establish immutable tag availability for $reference" >&2
  exit 1
}

require_digest "$DOCKERHUB_CANDIDATE"
require_digest "$GHCR_CANDIDATE"
require_immutable_or_absent "$DOCKERHUB_FULL"
require_immutable_or_absent "$DOCKERHUB_SHORT"
require_immutable_or_absent "$GHCR_FULL"
require_immutable_or_absent "$GHCR_SHORT"

echo "Candidate parity and immutable release-tag availability verified"
