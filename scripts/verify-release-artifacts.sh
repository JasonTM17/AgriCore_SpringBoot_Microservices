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
  PUBLISH_SHA \
  DOCKERHUB_REPOSITORY \
  GHCR_REPOSITORY \
  EXPECTED_CERTIFICATE_IDENTITY
do
  require_env "$variable"
done

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
# shellcheck source=registry-retry.sh
source "$SCRIPT_DIR/registry-retry.sh"
registry_retry_validate

verify_signature() {
  local reference="$1"
  registry_retry \
    "Verify signature for $reference" \
    cosign verify \
      --certificate-identity "$EXPECTED_CERTIFICATE_IDENTITY" \
      --certificate-oidc-issuer "https://token.actions.githubusercontent.com" \
      "$reference"
}

full="$PUBLISH_SHA"
short="${full:0:7}"

if ! expected="$(registry_digest "$DOCKERHUB_REPOSITORY:$full")"; then
  echo "Missing or unreadable Docker Hub full-SHA release tag: $DOCKERHUB_REPOSITORY:$full" >&2
  exit 1
fi

for repository in "$DOCKERHUB_REPOSITORY" "$GHCR_REPOSITORY"; do
  for tag in "$full" "$short"; do
    if ! actual="$(registry_digest "$repository:$tag")"; then
      echo "Release tag is missing or unreadable: $repository:$tag" >&2
      exit 1
    fi
    if [[ "$actual" != "$expected" ]]; then
      echo "Release verification failed for $repository:$tag: expected=$expected actual=$actual" >&2
      exit 1
    fi
  done
  verify_signature "$repository@$expected"
done

echo "Release artifacts verified for $PUBLISH_SHA: digest=$expected"
