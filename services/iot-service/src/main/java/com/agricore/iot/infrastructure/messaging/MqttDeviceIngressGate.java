package com.agricore.iot.infrastructure.messaging;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

@Component
public class MqttDeviceIngressGate {

    private static final Pattern SAFE_DEVICE_CODE = Pattern.compile("[A-Za-z0-9._-]+");
    private static final String INVALID_DEVICE_KEY = "__invalid_device__";
    private static final long NANOS_PER_SECOND = 1_000_000_000L;

    private final double permitsPerSecond;
    private final int burstCapacity;
    private final int maxInFlightPerDevice;
    private final int trackedDeviceCapacity;
    private final long idleTtlNanos;
    private final Map<String, DeviceBucket> buckets = new ConcurrentHashMap<>();
    private final AtomicLong acquisitionCount = new AtomicLong();
    private final Object bucketCreationLock = new Object();

    public MqttDeviceIngressGate(
            @Value("${agricore.mqtt.ingress.rate-per-second:10}") int permitsPerSecond,
            @Value("${agricore.mqtt.ingress.burst-capacity:16}") int burstCapacity,
            @Value("${agricore.mqtt.ingress.max-in-flight-per-device:4}") int maxInFlightPerDevice,
            @Value("${agricore.mqtt.ingress.tracked-device-capacity:10000}") int trackedDeviceCapacity,
            @Value("${agricore.mqtt.ingress.device-idle-ttl:PT10M}") Duration deviceIdleTtl
    ) {
        if (permitsPerSecond < 1 || permitsPerSecond > 10_000
                || burstCapacity < 1 || burstCapacity > 10_000
                || maxInFlightPerDevice < 1 || maxInFlightPerDevice > 64
                || trackedDeviceCapacity < 1 || trackedDeviceCapacity > 1_000_000
                || deviceIdleTtl.isNegative() || deviceIdleTtl.isZero()
                || deviceIdleTtl.compareTo(Duration.ofHours(24)) > 0) {
            throw new IllegalArgumentException("MQTT per-device ingress limits are outside safe bounds");
        }
        this.permitsPerSecond = permitsPerSecond;
        this.burstCapacity = burstCapacity;
        this.maxInFlightPerDevice = maxInFlightPerDevice;
        this.trackedDeviceCapacity = trackedDeviceCapacity;
        this.idleTtlNanos = deviceIdleTtl.toNanos();
    }

    public Optional<Permit> tryAcquire(String topic) {
        long now = System.nanoTime();
        if ((acquisitionCount.incrementAndGet() & 255L) == 0L) {
            evictIdleBuckets(now);
        }

        DeviceBucket bucket = acquireBucket(deviceKey(topic), now);
        if (bucket == null) {
            return Optional.empty();
        }
        return Optional.of(new Permit(bucket));
    }

    int trackedDeviceCount() {
        return buckets.size();
    }

    private DeviceBucket acquireBucket(String deviceKey, long now) {
        Acquisition existing = tryAcquireExisting(deviceKey, now);
        if (existing.found()) {
            return existing.bucket();
        }
        synchronized (bucketCreationLock) {
            existing = tryAcquireExisting(deviceKey, now);
            if (existing.found()) {
                return existing.bucket();
            }
            if (buckets.size() >= trackedDeviceCapacity) {
                evictIdleBuckets(now);
            }
            if (buckets.size() >= trackedDeviceCapacity) {
                return null;
            }
            DeviceBucket created = new DeviceBucket(burstCapacity, now);
            if (!created.tryAcquire(now, permitsPerSecond, burstCapacity, maxInFlightPerDevice)) {
                return null;
            }
            buckets.put(deviceKey, created);
            return created;
        }
    }

    private Acquisition tryAcquireExisting(String deviceKey, long now) {
        AcquisitionHolder holder = new AcquisitionHolder();
        buckets.computeIfPresent(deviceKey, (key, bucket) -> {
            holder.found = true;
            if (bucket.tryAcquire(now, permitsPerSecond, burstCapacity, maxInFlightPerDevice)) {
                holder.bucket = bucket;
            }
            return bucket;
        });
        return new Acquisition(holder.found, holder.bucket);
    }

    private void evictIdleBuckets(long now) {
        for (String deviceKey : buckets.keySet()) {
            buckets.computeIfPresent(
                    deviceKey,
                    (key, bucket) -> bucket.isIdle(now, idleTtlNanos) ? null : bucket
            );
        }
    }

    private static String deviceKey(String topic) {
        if (topic == null) {
            return INVALID_DEVICE_KEY;
        }
        String[] segments = topic.split("/", -1);
        if (segments.length != 4
                || !"agricore".equals(segments[0])
                || !"telemetry".equals(segments[1])
                || !"reading".equals(segments[3])
                || segments[2].isBlank()
                || segments[2].length() > 64
                || !SAFE_DEVICE_CODE.matcher(segments[2]).matches()) {
            return INVALID_DEVICE_KEY;
        }
        return segments[2].toUpperCase(Locale.ROOT);
    }

    private static final class DeviceBucket {

        private double availableTokens;
        private long lastRefillNanos;
        private long lastSeenNanos;
        private int inFlight;

        private DeviceBucket(int burstCapacity, long now) {
            availableTokens = burstCapacity;
            lastRefillNanos = now;
            lastSeenNanos = now;
        }

        private synchronized boolean tryAcquire(
                long now,
                double permitsPerSecond,
                int burstCapacity,
                int maxInFlight
        ) {
            refill(now, permitsPerSecond, burstCapacity);
            lastSeenNanos = now;
            if (inFlight >= maxInFlight || availableTokens < 1D) {
                return false;
            }
            availableTokens -= 1D;
            inFlight++;
            return true;
        }

        private synchronized void release() {
            if (inFlight > 0) {
                inFlight--;
            }
            lastSeenNanos = System.nanoTime();
        }

        private synchronized boolean isIdle(long now, long idleTtlNanos) {
            return inFlight == 0 && now - lastSeenNanos >= idleTtlNanos;
        }

        private void refill(long now, double permitsPerSecond, int burstCapacity) {
            long elapsed = Math.max(0L, now - lastRefillNanos);
            availableTokens = Math.min(
                    burstCapacity,
                    availableTokens + (elapsed * permitsPerSecond / NANOS_PER_SECOND)
            );
            lastRefillNanos = now;
        }
    }

    private record Acquisition(boolean found, DeviceBucket bucket) {
    }

    private static final class AcquisitionHolder {
        private boolean found;
        private DeviceBucket bucket;
    }

    public static final class Permit implements AutoCloseable {

        private final DeviceBucket bucket;
        private final AtomicBoolean closed = new AtomicBoolean();

        private Permit(DeviceBucket bucket) {
            this.bucket = bucket;
        }

        @Override
        public void close() {
            if (closed.compareAndSet(false, true)) {
                bucket.release();
            }
        }
    }
}
