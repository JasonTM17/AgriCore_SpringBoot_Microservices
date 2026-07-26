package com.agricore.iot.infrastructure.messaging;

import org.junit.jupiter.api.Test;

import java.time.Duration;

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

    private static String topic(String deviceCode) {
        return "agricore/telemetry/" + deviceCode + "/reading";
    }
}
