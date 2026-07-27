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
  CANDIDATE_TAG \
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

if ! dockerhub_digest="$(registry_digest "$DOCKERHUB_REPOSITORY:$CANDIDATE_TAG")"; then
  echo "Docker Hub candidate is missing or unreadable: $DOCKERHUB_REPOSITORY:$CANDIDATE_TAG" >&2
  exit 1
fi
if ! ghcr_digest="$(registry_digest "$GHCR_REPOSITORY:$CANDIDATE_TAG")"; then
  echo "GHCR candidate is missing or unreadable: $GHCR_REPOSITORY:$CANDIDATE_TAG" >&2
  exit 1
fi
if [[ "$dockerhub_digest" != "$ghcr_digest" ]]; then
  echo "Candidate parity failed: dockerhub=$dockerhub_digest ghcr=$ghcr_digest" >&2
  exit 1
fi

verify_signature "$DOCKERHUB_REPOSITORY@$dockerhub_digest"
verify_signature "$GHCR_REPOSITORY@$dockerhub_digest"

full="$PUBLISH_SHA"
short="${full:0:7}"
registry_retry \
  "Promote Docker Hub release tags" \
  docker buildx imagetools create \
    --tag "$DOCKERHUB_REPOSITORY:$full" \
    --tag "$DOCKERHUB_REPOSITORY:$short" \
    "$DOCKERHUB_REPOSITORY@$dockerhub_digest"
registry_retry \
  "Promote GHCR release tags" \
  docker buildx imagetools create \
    --tag "$GHCR_REPOSITORY:$full" \
    --tag "$GHCR_REPOSITORY:$short" \
    "$GHCR_REPOSITORY@$dockerhub_digest"

echo "Release tags promoted for $PUBLISH_SHA: digest=$dockerhub_digest"
