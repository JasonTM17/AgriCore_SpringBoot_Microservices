#!/usr/bin/env sh
set -eu

service_username="${MQTT_SERVICE_USERNAME:?MQTT_SERVICE_USERNAME is required}"
service_password="${MQTT_SERVICE_PASSWORD:?MQTT_SERVICE_PASSWORD is required}"
device_password_seed="${MQTT_DEVICE_PASSWORD_SEED:?MQTT_DEVICE_PASSWORD_SEED is required}"
device_count="${MQTT_DEVICE_COUNT:-1}"
device_prefix="${MQTT_DEVICE_PREFIX:-DEMO-SOIL}"
device_users="${MQTT_DEVICE_USERS:-}"

case "$service_username:$device_count:$device_prefix" in
  *[!A-Za-z0-9._,:-]*|:*|*::*|*:)
    echo "MQTT service username, device count, or prefix contains unsupported characters" >&2
    exit 2
    ;;
esac
if [ "$device_count" -lt 1 ] || [ "$device_count" -gt 1000 ]; then
  echo "MQTT_DEVICE_COUNT must be between 1 and 1000" >&2
  exit 2
fi

password_file=/tmp/agricore-mqtt-passwords
acl_file=/tmp/agricore-mqtt-acl
# These files survive a container restart in its writable layer. They are
# chowned to the broker account below, while `mosquitto_passwd -c` refuses to
# overwrite a file owned by another account. Removing only these fixed,
# entrypoint-owned temporary files makes restarts deterministic.
rm -f "$password_file" "$acl_file"
mosquitto_passwd -b -c "$password_file" "$service_username" "$service_password"
printf 'user %s\ntopic read agricore/telemetry/+/reading\ntopic write agricore/health\n' \
  "$service_username" > "$acl_file"

if [ -z "$device_users" ]; then
  index=1
  while [ "$index" -le "$device_count" ]; do
    device_users="${device_users}$(printf '%s-%03d' "$device_prefix" "$index"),"
    index=$((index + 1))
  done
fi

old_ifs="$IFS"
IFS=','
for device_username in $device_users; do
  [ -n "$device_username" ] || continue
  case "$device_username" in
    *[!A-Za-z0-9._-]*)
      echo "MQTT device usernames may only contain letters, digits, dot, underscore, and hyphen" >&2
      exit 2
      ;;
  esac
  device_password="$(printf '%s:%s' "$device_password_seed" "$device_username" | sha256sum | cut -c1-32)"
  mosquitto_passwd -b "$password_file" "$device_username" "$device_password"
  printf 'user %s\ntopic write agricore/telemetry/%s/reading\n' \
    "$device_username" "$device_username" >> "$acl_file"
done
IFS="$old_ifs"

chown mosquitto:mosquitto "$password_file" "$acl_file"
chmod 0640 "$password_file" "$acl_file"
chown -R mosquitto:mosquitto /mosquitto/data
exec mosquitto -c /mosquitto/config/mosquitto.conf
