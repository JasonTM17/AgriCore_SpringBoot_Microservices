package com.agricore.iot.infrastructure.messaging;

import com.agricore.common.event.EventTypes;
import com.agricore.iot.infrastructure.persistence.DeviceJpaRepository;
import com.agricore.iot.infrastructure.persistence.OutboxJpaRepository;
import com.agricore.iot.infrastructure.persistence.SensorReadingJpaRepository;
import com.agricore.iot.infrastructure.persistence.entity.DeviceEntity;
import org.eclipse.paho.client.mqttv3.MqttAsyncClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "agricore.mqtt.enabled=true",
        "agricore.mqtt.broker=tcp://127.0.0.1:1883",
        "agricore.mqtt.allow-insecure=true",
        "agricore.mqtt.client-id=agricore-iot-broker-integration",
        "agricore.mqtt.username=agricore_iot",
        "agricore.mqtt.password=agricore_iot_dev_change_me",
        "agricore.mqtt.reconnect-delay-seconds=1"
})
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "MQTT_BROKER_INTEGRATION", matches = "true")
class MqttBrokerIntegrationTest {

    private static final String BROKER = "tcp://127.0.0.1:1883";
    private static final String DEVICE_CODE = "DEMO-SOIL-001";

    @Autowired
    private MqttTelemetryListener listener;
    @Autowired
    private DeviceJpaRepository deviceRepository;
    @Autowired
    private SensorReadingJpaRepository readingRepository;
    @Autowired
    private OutboxJpaRepository outboxRepository;

    @Test
    void authenticatedBrokerDeliverySurvivesClientReconnectAndRemainsIdempotent() throws Exception {
        registerDeviceIfNeeded();
        await(listener::isReady, "listener subscription did not become ready");
        forceClientReconnect();
        await(listener::isReady, "listener did not resubscribe after reconnect");

        UUID readingId = UUID.randomUUID();
        String payload = """
                {"readingId":"%s","deviceCode":"%s","metricType":"SOIL_MOISTURE","metricValue":10.5000,"unit":"PERCENT","recordedAt":"%s"}
                """.formatted(readingId, DEVICE_CODE, Instant.now());
        publishAsDevice(payload);
        publishAsDevice(payload);

        await(() -> readingRepository.existsById(readingId), "broker reading was not persisted");
        assertThat(outboxRepository.findAll()).filteredOn(event ->
                        EventTypes.SENSOR_READING_RECEIVED.equals(event.getEventType())
                                && event.getPayload().contains(readingId.toString()))
                .singleElement();
        assertThat(outboxRepository.findAll()).filteredOn(event ->
                        EventTypes.SENSOR_THRESHOLD_EXCEEDED.equals(event.getEventType())
                                && event.getPayload().contains(readingId.toString()))
                .singleElement();
    }

    private void forceClientReconnect() throws Exception {
        MqttAsyncClient collision = new MqttAsyncClient(
                BROKER, "agricore-iot-broker-integration", new MemoryPersistence());
        collision.connect(options("agricore_iot", "agricore_iot_dev_change_me")).waitForCompletion(3_000L);
        Thread.sleep(250L);
        if (collision.isConnected()) {
            collision.disconnect().waitForCompletion(2_000L);
        }
        collision.close();
    }

    private void publishAsDevice(String payload) throws Exception {
        String password = derivedDevicePassword(DEVICE_CODE);
        MqttAsyncClient publisher = new MqttAsyncClient(
                BROKER, "agricore-test-publisher-" + UUID.randomUUID(), new MemoryPersistence());
        publisher.connect(options(DEVICE_CODE, password)).waitForCompletion(3_000L);
        publisher.publish("agricore/telemetry/" + DEVICE_CODE + "/reading",
                payload.getBytes(StandardCharsets.UTF_8), 1, false).waitForCompletion(3_000L);
        publisher.disconnect().waitForCompletion(2_000L);
        publisher.close();
    }

    private static MqttConnectOptions options(String username, String password) {
        MqttConnectOptions options = new MqttConnectOptions();
        options.setCleanSession(true);
        options.setConnectionTimeout(3);
        options.setUserName(username);
        options.setPassword(password.toCharArray());
        return options;
    }

    private static String derivedDevicePassword(String deviceCode) throws Exception {
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(
                ("agricore_device_seed_dev_change_me:" + deviceCode).getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(hash).substring(0, 32);
    }

    private void registerDeviceIfNeeded() {
        if (deviceRepository.existsByDeviceCodeIgnoreCase(DEVICE_CODE)) {
            return;
        }
        DeviceEntity device = new DeviceEntity();
        device.setId(UUID.randomUUID());
        device.setDeviceCode(DEVICE_CODE);
        device.setPlotId(UUID.randomUUID());
        device.setName("Broker integration probe");
        device.setStatus("ACTIVE");
        device.setCreatedAt(Instant.now());
        deviceRepository.save(device);
    }

    private static void await(BooleanSupplier condition, String failureMessage) throws InterruptedException {
        long deadline = System.nanoTime() + 10_000_000_000L;
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
            Thread.sleep(100L);
        }
        assertThat(condition.getAsBoolean()).as(failureMessage).isTrue();
    }
}
