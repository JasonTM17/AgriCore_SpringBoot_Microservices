#!/bin/sh
set -eu

max_attempts="${IOT_TIMESCALE_INIT_MAX_ATTEMPTS:-60}"
retry_seconds="${IOT_TIMESCALE_INIT_RETRY_SECONDS:-2}"

case "$max_attempts:$retry_seconds" in
    *[!0-9:]* | 0:* | *:0)
        echo "IoT Timescale initialization retry settings must be positive integers" >&2
        exit 2
        ;;
esac

attempt=1
last_error="database has not been contacted"
while [ "$attempt" -le "$max_attempts" ]; do
    if create_output="$(
        psql -v ON_ERROR_STOP=1 -d agricore_iot \
            -c 'CREATE EXTENSION IF NOT EXISTS timescaledb' 2>&1
    )"; then
        if version="$(
            psql -v ON_ERROR_STOP=1 -At -d agricore_iot \
                -c "SELECT extversion FROM pg_extension WHERE extname = 'timescaledb'" 2>&1
        )" && [ -n "$version" ]; then
            echo "TimescaleDB $version is ready in agricore_iot"
            exit 0
        fi
        last_error="$version"
    else
        last_error="$create_output"
    fi

    if [ "$attempt" -lt "$max_attempts" ]; then
        echo "Waiting for agricore_iot Timescale initialization ($attempt/$max_attempts)"
        sleep "$retry_seconds"
    fi
    attempt=$((attempt + 1))
done

echo "Could not initialize TimescaleDB in agricore_iot after $max_attempts attempts" >&2
echo "$last_error" >&2
exit 1
