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

REGISTRY_RETRY_ATTEMPTS="${REGISTRY_RETRY_ATTEMPTS:-5}"
REGISTRY_RETRY_DELAY_SECONDS="${REGISTRY_RETRY_DELAY_SECONDS:-5}"
REGISTRY_RETRY_MAX_DELAY_SECONDS="${REGISTRY_RETRY_MAX_DELAY_SECONDS:-60}"

if [[ ! "$REGISTRY_RETRY_ATTEMPTS" =~ ^[1-9][0-9]*$ ]]; then
  echo "REGISTRY_RETRY_ATTEMPTS must be a positive integer" >&2
  exit 2
fi
if [[ ! "$REGISTRY_RETRY_DELAY_SECONDS" =~ ^[0-9]+$ ]]; then
  echo "REGISTRY_RETRY_DELAY_SECONDS must be a non-negative integer" >&2
  exit 2
fi
if [[ ! "$REGISTRY_RETRY_MAX_DELAY_SECONDS" =~ ^[0-9]+$ ]]; then
  echo "REGISTRY_RETRY_MAX_DELAY_SECONDS must be a non-negative integer" >&2
  exit 2
fi

retry_delay() {
  local attempt="$1"
  local delay="$REGISTRY_RETRY_DELAY_SECONDS"
  local step=1
  local jitter=0

  if (( delay > REGISTRY_RETRY_MAX_DELAY_SECONDS )); then
    delay="$REGISTRY_RETRY_MAX_DELAY_SECONDS"
  fi

  while (( step < attempt && delay < REGISTRY_RETRY_MAX_DELAY_SECONDS )); do
    delay=$((delay * 2))
    if (( delay > REGISTRY_RETRY_MAX_DELAY_SECONDS )); then
      delay="$REGISTRY_RETRY_MAX_DELAY_SECONDS"
    fi
    step=$((step + 1))
  done

  if (( delay > 0 && delay < REGISTRY_RETRY_MAX_DELAY_SECONDS )); then
    jitter=$((RANDOM % (delay / 4 + 1)))
    delay=$((delay + jitter))
    if (( delay > REGISTRY_RETRY_MAX_DELAY_SECONDS )); then
      delay="$REGISTRY_RETRY_MAX_DELAY_SECONDS"
    fi
  fi

  printf '%s\n' "$delay"
}

retry() {
  local label="$1"
  shift
  local attempt=1
  local delay
  local status

  while true; do
    if "$@"; then
      return 0
    else
      status=$?
    fi

    if (( attempt >= REGISTRY_RETRY_ATTEMPTS )); then
      echo "$label failed after $attempt attempt(s), exit=$status" >&2
      return "$status"
    fi

    delay="$(retry_delay "$attempt")"
    echo "$label failed on attempt $attempt/$REGISTRY_RETRY_ATTEMPTS, exit=$status; retrying in ${delay}s" >&2
    sleep "$delay"
    attempt=$((attempt + 1))
  done
}

inspect_with_retry() {
  local reference="$1"
  local attempt=1
  local delay
  local output
  local status

  while true; do
    if output="$(docker buildx imagetools inspect "$reference" 2>&1)"; then
      printf '%s\n' "$output"
      return 0
    else
      status=$?
    fi

    if [[ -n "$output" ]]; then
      printf '%s\n' "$output" >&2
    fi
    if (( attempt >= REGISTRY_RETRY_ATTEMPTS )); then
      echo "Inspect $reference failed after $attempt attempt(s), exit=$status" >&2
      return "$status"
    fi

    delay="$(retry_delay "$attempt")"
    echo "Inspect $reference failed on attempt $attempt/$REGISTRY_RETRY_ATTEMPTS, exit=$status; retrying in ${delay}s" >&2
    sleep "$delay"
    attempt=$((attempt + 1))
  done
}

digest_of() {
  local reference="$1"
  local output
  local digest

  if ! output="$(inspect_with_retry "$reference")"; then
    return 1
  fi

  digest="$(awk '/^Digest:/ { print $2; exit }' <<< "$output")"
  if [[ ! "$digest" =~ ^sha256:[0-9a-f]{64}$ ]]; then
    echo "Registry returned no valid digest for $reference" >&2
    return 1
  fi
  printf '%s\n' "$digest"
}

verify_signature() {
  local reference="$1"
  retry \
    "Verify signature for $reference" \
    cosign verify \
      --certificate-identity "$EXPECTED_CERTIFICATE_IDENTITY" \
      --certificate-oidc-issuer "https://token.actions.githubusercontent.com" \
      "$reference"
}

full="$PUBLISH_SHA"
short="${full:0:7}"

if ! expected="$(digest_of "$DOCKERHUB_REPOSITORY:$full")"; then
  echo "Missing or unreadable Docker Hub full-SHA release tag: $DOCKERHUB_REPOSITORY:$full" >&2
  exit 1
fi

for repository in "$DOCKERHUB_REPOSITORY" "$GHCR_REPOSITORY"; do
  for tag in "$full" "$short"; do
    if ! actual="$(digest_of "$repository:$tag")"; then
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
