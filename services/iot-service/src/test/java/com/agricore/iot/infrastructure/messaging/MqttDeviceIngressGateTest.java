package com.agricore.iot.infrastructure.messaging;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class MqttDeviceIngressGateTest {

    @Test
    void oneDeviceCannotConsumeAnotherDevicesInflightCapacity() {
        MqttDeviceIngressGate gate = new MqttDeviceIngressGate(
                100, 100, 2, 100, Duration.ofMinutes(1));

        MqttDeviceIngressGate.Permit first = gate.tryAcquire(topic("DEVICE-A")).orElseThrow();
        MqttDeviceIngressGate.Permit second = gate.tryAcquire(topic("DEVICE-A")).orElseThrow();

        assertThat(gate.tryAcquire(topic("DEVICE-A"))).isEmpty();
        assertThat(gate.tryAcquire(topic("DEVICE-B"))).isPresent()
                .get()
                .satisfies(MqttDeviceIngressGate.Permit::close);

        first.close();
        assertThat(gate.tryAcquire(topic("DEVICE-A"))).isPresent()
                .get()
                .satisfies(MqttDeviceIngressGate.Permit::close);
        second.close();
    }

    @Test
    void tokenBucketBoundsSequentialBurstsAndNormalizesDeviceIdentity() {
        MqttDeviceIngressGate gate = new MqttDeviceIngressGate(
                1, 2, 2, 100, Duration.ofMinutes(1));

        gate.tryAcquire(topic("device-a")).orElseThrow().close();
        gate.tryAcquire(topic("DEVICE-A")).orElseThrow().close();

        assertThat(gate.tryAcquire(topic("Device-A"))).isEmpty();
        assertThat(gate.trackedDeviceCount()).isEqualTo(1);
    }

    @Test
    void trackedDeviceCapacityIsBoundedWhileActiveDeviceIsRetained() {
        MqttDeviceIngressGate gate = new MqttDeviceIngressGate(
                10, 10, 1, 1, Duration.ofMinutes(1));

        MqttDeviceIngressGate.Permit active = gate.tryAcquire(topic("DEVICE-A")).orElseThrow();

        assertThat(gate.tryAcquire(topic("DEVICE-B"))).isEmpty();
        assertThat(gate.trackedDeviceCount()).isEqualTo(1);
        active.close();
    }

    @Test
    void invalidTopicsShareOneBoundedBucketInsteadOfGrowingState() {
        MqttDeviceIngressGate gate = new MqttDeviceIngressGate(
                10, 10, 1, 10, Duration.ofMinutes(1));

        MqttDeviceIngressGate.Permit invalid = gate.tryAcquire("invalid/topic").orElseThrow();

        assertThat(gate.tryAcquire("agricore/telemetry/way-too-long-"
                + "x".repeat(80) + "/reading")).isEmpty();
        assertThat(gate.trackedDeviceCount()).isEqualTo(1);
        invalid.close();
    }

    @Test
    void idleEvictionCannotDetachAConcurrentDevicePermit() throws Exception {
        MqttDeviceIngressGate gate = new MqttDeviceIngressGate(
                10_000, 10_000, 1, 1, Duration.ofNanos(1));
        AtomicInteger inFlight = new AtomicInteger();
        AtomicInteger maximumInFlight = new AtomicInteger();
        CountDownLatch ready = new CountDownLatch(12);
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(12);

        try {
            for (int worker = 0; worker < 12; worker++) {
                executor.submit(() -> {
                    ready.countDown();
                    start.await(5, TimeUnit.SECONDS);
                    for (int attempt = 0; attempt < 600; attempt++) {
                        gate.tryAcquire(topic("DEVICE-RACE")).ifPresent(permit -> {
                            int current = inFlight.incrementAndGet();
                            maximumInFlight.accumulateAndGet(current, Math::max);
                            Thread.onSpinWait();
                            inFlight.decrementAndGet();
                            permit.close();
                        });
                    }
                    return null;
                });
            }
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            executor.shutdown();
            assertThat(executor.awaitTermination(15, TimeUnit.SECONDS)).isTrue();

            assertThat(maximumInFlight).hasValue(1);
            assertThat(gate.trackedDeviceCount()).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    private static String topic(String deviceCode) {
        return "agricore/telemetry/" + deviceCode + "/reading";
    }
}
