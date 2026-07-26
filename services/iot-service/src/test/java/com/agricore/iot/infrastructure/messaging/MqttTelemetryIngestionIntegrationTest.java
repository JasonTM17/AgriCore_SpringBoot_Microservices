package com.agricore.iot.infrastructure.messaging;

import com.agricore.common.event.EventTypes;
import com.agricore.iot.application.service.IotApplicationService;
import com.agricore.iot.application.service.IotMetrics;
import com.agricore.iot.infrastructure.persistence.DeviceJpaRepository;
import com.agricore.iot.infrastructure.persistence.OutboxJpaRepository;
import com.agricore.iot.infrastructure.persistence.SensorReadingJpaRepository;
import com.agricore.iot.infrastructure.persistence.entity.DeviceEntity;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class MqttTelemetryIngestionIntegrationTest {

    @Autowired
    private IotApplicationService iotApplicationService;
    @Autowired
    private IotMetrics metrics;
    @Autowired
    private MqttTelemetryPayloadParser payloadParser;
    @Autowired
    private DeviceJpaRepository deviceRepository;
    @Autowired
    private SensorReadingJpaRepository readingRepository;
    @Autowired
    private OutboxJpaRepository outboxRepository;

    @Test
    void adapterPathPersistsOneReadingAndOneAlertEventAcrossRedelivery() {
        String deviceCode = "MQTT-" + UUID.randomUUID();
        UUID readingId = UUID.randomUUID();
        registerDevice(deviceCode);
        MqttTelemetryListener listener = listener();
        MqttMessage message = new MqttMessage(("""
                {"readingId":"%s","deviceCode":"%s","metricType":"SOIL_MOISTURE","metricValue":10.5000,"unit":"PERCENT","recordedAt":"%s"}
                """).formatted(readingId, deviceCode, Instant.now()).getBytes());
        message.setQos(1);

        listener.onMessage("agricore/telemetry/" + deviceCode + "/reading", message);
        listener.onMessage("agricore/telemetry/" + deviceCode + "/reading", message);
        listener.stop();

        assertThat(readingRepository.findById(readingId)).isPresent();
        assertThat(outboxRepository.findAll()).filteredOn(event ->
                        EventTypes.SENSOR_READING_RECEIVED.equals(event.getEventType())
                                && event.getPayload().contains(readingId.toString()))
                .singleElement();
        assertThat(outboxRepository.findAll()).filteredOn(event ->
                        EventTypes.SENSOR_THRESHOLD_EXCEEDED.equals(event.getEventType())
                                && event.getPayload().contains(readingId.toString()))
                .singleElement();
    }

    private MqttTelemetryListener listener() {
        MqttTelemetryMessageProcessor processor = new MqttTelemetryMessageProcessor(
                iotApplicationService, metrics, payloadParser);
        return new MqttTelemetryListener(
                metrics, processor, new MqttDeviceIngressGate(
                100, 100, 4, 100, Duration.ofMinutes(1)), "tcp://localhost:1883", true,
                "integration-test", "agricore/telemetry/+/reading", 1, 1, 1, "user", "password");
    }

    private void registerDevice(String deviceCode) {
        DeviceEntity device = new DeviceEntity();
        device.setId(UUID.randomUUID());
        device.setDeviceCode(deviceCode);
        device.setPlotId(UUID.randomUUID());
        device.setName("MQTT integration probe");
        device.setStatus("ACTIVE");
        device.setCreatedAt(Instant.now());
        deviceRepository.save(device);
    }
}
