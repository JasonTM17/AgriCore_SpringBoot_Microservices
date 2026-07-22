#!/usr/bin/env sh
set -eu

broker="${MQTT_BROKER:-mqtt}"
port="${MQTT_PORT:-1883}"
device_password_seed="${MQTT_DEVICE_PASSWORD_SEED:?MQTT_DEVICE_PASSWORD_SEED is required}"
device_count="${MQTT_DEVICE_COUNT:-1}"
iterations="${MQTT_ITERATIONS:-1}"
interval_seconds="${MQTT_INTERVAL_SECONDS:-5}"
device_prefix="${MQTT_DEVICE_PREFIX:-DEMO-SOIL}"
plot_map="${MQTT_PLOT_MAP:-DEMO-SOIL-001=PLOT-DEMO-001}"
metric_type="${MQTT_METRIC_TYPE:-SOIL_MOISTURE}"
unit="${MQTT_UNIT:-PERCENT}"
minimum="${MQTT_MIN_VALUE:-30}"
maximum="${MQTT_MAX_VALUE:-70}"
anomaly_minimum="${MQTT_ANOMALY_MIN_VALUE:-5}"
anomaly_maximum="${MQTT_ANOMALY_MAX_VALUE:-18}"
anomaly_probability="${MQTT_ANOMALY_PROBABILITY_PERCENT:-20}"
seed="${MQTT_SEED:-42}"
recorded_at_override="${MQTT_RECORDED_AT:-}"

case "$device_count:$iterations:$interval_seconds:$anomaly_probability:$seed" in
  *[!0-9:]*|:*|*::*|*:)
    echo "device count, iterations, interval, anomaly probability, and seed must be non-negative integers" >&2
    exit 2
    ;;
esac
if [ "$device_count" -lt 1 ] || [ "$device_count" -gt 1000 ] \
  || [ "$iterations" -lt 1 ] || [ "$iterations" -gt 100000 ] \
  || [ $((device_count * iterations)) -gt 100000 ] \
  || [ "$interval_seconds" -gt 86400 ] || [ "$anomaly_probability" -gt 100 ] \
  || [ "$seed" -gt 2147 ]; then
  echo "simulator bounds exceeded (max 1000 devices, 100000 messages, 86400s interval, seed 2147)" >&2
  exit 2
fi
case "$device_prefix:$metric_type:$unit" in
  *[!A-Za-z0-9._:-]*)
    echo "device prefix, metric type, and unit contain unsupported characters" >&2
    exit 2
    ;;
esac

random_state=$((seed % 2147483647))
next_random() {
  random_state=$(((random_state * 48271) % 2147483647))
  random_value=$((random_state % 10000))
}

mapped_device() {
  printf '%s' "$plot_map" | awk -F',' -v position="$1" '{ if (position <= NF) print $position }'
}

reading_value() {
  range_min="$minimum"
  range_max="$maximum"
  if [ "$1" -lt $((anomaly_probability * 100)) ]; then
    range_min="$anomaly_minimum"
    range_max="$anomaly_maximum"
  fi
  awk -v lower="$range_min" -v upper="$range_max" -v sample="$2" \
    'BEGIN { printf "%.4f", lower + ((upper - lower) * sample / 9999) }'
}

iteration=1
while [ "$iteration" -le "$iterations" ]; do
  device_index=1
  while [ "$device_index" -le "$device_count" ]; do
    mapping="$(mapped_device "$device_index")"
    if [ -n "$mapping" ] && [ "${mapping#*=}" != "$mapping" ]; then
      device_code="${mapping%%=*}"
      plot_code="${mapping#*=}"
    else
      device_code="$(printf '%s-%03d' "$device_prefix" "$device_index")"
      plot_code="UNMAPPED"
    fi
    case "$device_code" in
      *[!A-Za-z0-9._-]*)
        echo "device and plot codes may only contain letters, digits, dot, underscore, and hyphen" >&2
        exit 2
        ;;
    esac
    case "$plot_code" in
      *[!A-Za-z0-9._-]*)
        echo "device and plot codes may only contain letters, digits, dot, underscore, and hyphen" >&2
        exit 2
        ;;
    esac

    next_random
    anomaly_sample="$random_value"
    next_random
    value="$(reading_value "$anomaly_sample" "$random_value")"
    sequence=$((seed * 1000000 + iteration * 1000 + device_index))
    reading_id="$(printf '00000000-0000-4000-8000-%012d' "$sequence")"
    recorded_at="${recorded_at_override:-$(date -u +%Y-%m-%dT%H:%M:%SZ)}"
    payload="$(printf '{"readingId":"%s","deviceCode":"%s","metricType":"%s","metricValue":%s,"unit":"%s","recordedAt":"%s"}' \
      "$reading_id" "$device_code" "$metric_type" "$value" "$unit" "$recorded_at")"

    device_password="$(printf '%s:%s' "$device_password_seed" "$device_code" | sha256sum | cut -c1-32)"
    mosquitto_pub -h "$broker" -p "$port" -q 1 -u "$device_code" -P "$device_password" \
      -t "agricore/telemetry/${device_code}/reading" -m "$payload"
    echo "published readingId=${reading_id} deviceCode=${device_code} plotCode=${plot_code} value=${value}"
    device_index=$((device_index + 1))
  done
  iteration=$((iteration + 1))
  if [ "$iteration" -le "$iterations" ] && [ "$interval_seconds" -gt 0 ]; then
    sleep "$interval_seconds"
  fi
done
