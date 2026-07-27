#!/usr/bin/env bash

REGISTRY_RETRY_ATTEMPTS="${REGISTRY_RETRY_ATTEMPTS:-5}"
REGISTRY_RETRY_DELAY_SECONDS="${REGISTRY_RETRY_DELAY_SECONDS:-5}"
REGISTRY_RETRY_MAX_DELAY_SECONDS="${REGISTRY_RETRY_MAX_DELAY_SECONDS:-60}"

registry_retry_validate() {
  if [[ ! "$REGISTRY_RETRY_ATTEMPTS" =~ ^[1-9][0-9]*$ ]]; then
    echo "REGISTRY_RETRY_ATTEMPTS must be a positive integer" >&2
    return 2
  fi
  if [[ ! "$REGISTRY_RETRY_DELAY_SECONDS" =~ ^[0-9]+$ ]]; then
    echo "REGISTRY_RETRY_DELAY_SECONDS must be a non-negative integer" >&2
    return 2
  fi
  if [[ ! "$REGISTRY_RETRY_MAX_DELAY_SECONDS" =~ ^[0-9]+$ ]]; then
    echo "REGISTRY_RETRY_MAX_DELAY_SECONDS must be a non-negative integer" >&2
    return 2
  fi
}

registry_retry_delay() {
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

registry_retry() {
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

    delay="$(registry_retry_delay "$attempt")"
    echo "$label failed on attempt $attempt/$REGISTRY_RETRY_ATTEMPTS, exit=$status; retrying in ${delay}s" >&2
    sleep "$delay"
    attempt=$((attempt + 1))
  done
}

registry_inspect() {
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
      return 1
    fi

    delay="$(registry_retry_delay "$attempt")"
    echo "Inspect $reference failed on attempt $attempt/$REGISTRY_RETRY_ATTEMPTS, exit=$status; retrying in ${delay}s" >&2
    sleep "$delay"
    attempt=$((attempt + 1))
  done
}

registry_digest() {
  local reference="$1"
  local output
  local digest

  if ! output="$(registry_inspect "$reference")"; then
    return 1
  fi

  digest="$(awk '/^Digest:/ { print $2; exit }' <<< "$output")"
  if [[ ! "$digest" =~ ^sha256:[0-9a-f]{64}$ ]]; then
    echo "Registry returned no valid digest for $reference" >&2
    return 1
  fi
  printf '%s\n' "$digest"
}

registry_digest_or_absent() {
  local reference="$1"
  local attempt=1
  local delay
  local digest
  local output
  local status

  while true; do
    if output="$(docker buildx imagetools inspect "$reference" 2>&1)"; then
      digest="$(awk '/^Digest:/ { print $2; exit }' <<< "$output")"
      if [[ ! "$digest" =~ ^sha256:[0-9a-f]{64}$ ]]; then
        echo "Registry returned no valid digest for $reference" >&2
        return 1
      fi
      printf '%s\n' "$digest"
      return 0
    else
      status=$?
    fi

    if grep -Eiq '(manifest unknown|no such manifest|(^|[[:space:]:])not found([[:space:]:]|$))' <<< "$output"; then
      return 3
    fi
    if [[ -n "$output" ]]; then
      printf '%s\n' "$output" >&2
    fi
    if (( attempt >= REGISTRY_RETRY_ATTEMPTS )); then
      echo "Inspect $reference failed after $attempt attempt(s), exit=$status" >&2
      return 1
    fi

    delay="$(registry_retry_delay "$attempt")"
    echo "Inspect $reference failed on attempt $attempt/$REGISTRY_RETRY_ATTEMPTS, exit=$status; retrying in ${delay}s" >&2
    sleep "$delay"
    attempt=$((attempt + 1))
  done
}
