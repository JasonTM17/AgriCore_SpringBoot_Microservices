package com.agricore.iot.infrastructure.messaging;

import com.agricore.iot.application.service.IotApplicationService;
import com.agricore.iot.application.service.IotMetrics;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MqttTelemetryListenerTest {

    private final IotApplicationService iotService = mock(IotApplicationService.class);
    private final IotMetrics metrics = mock(IotMetrics.class);
    private MqttTelemetryListener listener;

    @BeforeEach
    void setUp() {
        MqttTelemetryPayloadParser payloadParser = new MqttTelemetryPayloadParser(
                new ObjectMapper(), Validation.buildDefaultValidatorFactory().getValidator());
        MqttTelemetryMessageProcessor messageProcessor = new MqttTelemetryMessageProcessor(
                iotService, metrics, payloadParser);
        listener = new MqttTelemetryListener(
                metrics,
                messageProcessor,
                ingressGate(),
                "tcp://localhost:1883",
                true,
                "test-client",
                "agricore/telemetry/+/reading",
                1,
                1,
                1,
                "test-user",
                "test-password"
        );
    }

    @AfterEach
    void tearDown() {
        listener.stop();
    }

    @Test
    void acceptsValidTelemetryAndBindsTopicDevice() {
        UUID readingId = UUID.randomUUID();
        MqttMessage message = message("""
                {"readingId":"%s","deviceCode":"DEMO-SOIL-001","metricType":"SOIL_MOISTURE","metricValue":42.5,"unit":"PERCENT"}
                """.formatted(readingId));

        listener.onMessage("agricore/telemetry/DEMO-SOIL-001/reading", message);

        verify(iotService).ingestFromMqtt(argThat(request ->
                readingId.equals(request.readingId())
                        && "DEMO-SOIL-001".equals(request.deviceCode())
                        && request.metricValue().doubleValue() == 42.5));
        verify(metrics).recordMqttOutcome("accepted");
    }

    @Test
    void rejectsTopicPayloadDeviceMismatchAndMissingStableReadingId() {
        listener.onMessage("agricore/telemetry/DEMO-SOIL-001/reading", message("""
                {"deviceCode":"OTHER","metricType":"SOIL_MOISTURE","metricValue":42.5,"unit":"PERCENT"}
                """));

        verify(iotService, never()).ingestFromMqtt(argThat(request -> true));
        verify(metrics).recordMqttOutcome("rejected");
    }

    @Test
    void rejectsOversizedPayloadWithoutThrowingFromCallback() {
        MqttMessage message = new MqttMessage(new byte[16 * 1024 + 1]);

        assertThatNoException().isThrownBy(() ->
                listener.onMessage("agricore/telemetry/DEMO-SOIL-001/reading", message));
        verify(iotService, never()).ingestFromMqtt(argThat(request -> true));
        verify(metrics).recordMqttOutcome("oversized");
    }

    @Test
    void rejectsDeliveryThatDoesNotUseContractQos() {
        MqttMessage message = message("""
                {"readingId":"%s","deviceCode":"DEMO-SOIL-001","metricType":"SOIL_MOISTURE","metricValue":42.5,"unit":"PERCENT"}
                """.formatted(UUID.randomUUID()));
        message.setQos(0);

        listener.onMessage("agricore/telemetry/DEMO-SOIL-001/reading", message);

        verify(iotService, never()).ingestFromMqtt(any());
        verify(metrics).recordMqttOutcome("rejected");
    }

    @Test
    void rejectsUnknownPayloadFields() {
        UUID readingId = UUID.randomUUID();
        listener.onMessage("agricore/telemetry/DEMO-SOIL-001/reading", message("""
                {"readingId":"%s","deviceCode":"DEMO-SOIL-001","metricType":"SOIL_MOISTURE","metricValue":42.5,"unit":"PERCENT","unexpected":true}
                """.formatted(readingId)));

        verify(iotService, never()).ingestFromMqtt(argThat(request -> true));
        verify(metrics).recordMqttOutcome("rejected");
    }

    @Test
    void rejectsDuplicateKeysTrailingTokensAndStaleTimestamps() {
        UUID readingId = UUID.randomUUID();
        String base = """
                {"readingId":"%s","deviceCode":"DEMO-SOIL-001","metricType":"SOIL_MOISTURE","metricValue":42.5,"unit":"PERCENT"}
                """.formatted(readingId).trim();
        listener.onMessage("agricore/telemetry/DEMO-SOIL-001/reading", message(base + " true"));
        listener.onMessage("agricore/telemetry/DEMO-SOIL-001/reading", message(base.replace(
                "\"unit\":\"PERCENT\"", "\"unit\":\"PERCENT\",\"unit\":\"PCT\"")));
        listener.onMessage("agricore/telemetry/DEMO-SOIL-001/reading", message(base.replace(
                "}", ",\"recordedAt\":\"" + Instant.now().minus(31, ChronoUnit.DAYS) + "\"}")));

        verify(iotService, never()).ingestFromMqtt(any());
        verify(metrics, times(3)).recordMqttOutcome("rejected");
    }

    @Test
    void leavesTransientProcessingFailureUnacknowledgedForBrokerRedelivery() {
        when(iotService.ingestFromMqtt(any())).thenThrow(new IllegalStateException("database unavailable"));
        UUID readingId = UUID.randomUUID();

        listener.onMessage("agricore/telemetry/DEMO-SOIL-001/reading", message("""
                {"readingId":"%s","deviceCode":"DEMO-SOIL-001","metricType":"SOIL_MOISTURE","metricValue":42.5,"unit":"PERCENT"}
                """.formatted(readingId)));

        verify(iotService, times(3)).ingestFromMqtt(any());
        verify(metrics).recordMqttOutcome("processing_failed");
    }

    @Test
    void rejectsMalformedTimestampAndTopicWithoutEscapingCallback() {
        UUID readingId = UUID.randomUUID();
        MqttMessage message = message("""
                {"readingId":"%s","deviceCode":"DEMO-SOIL-001","metricType":"SOIL_MOISTURE","metricValue":42.5,"unit":"PERCENT","recordedAt":"not-an-instant"}
                """.formatted(readingId));

        assertThatNoException().isThrownBy(() -> listener.onMessage("invalid/topic", message));
        verify(iotService, never()).ingestFromMqtt(argThat(request -> true));
        verify(metrics).recordMqttOutcome("rejected");
    }

    @Test
    void requiresTlsUnlessInsecureTransportIsExplicitlyEnabled() {
        MqttTelemetryPayloadParser payloadParser = new MqttTelemetryPayloadParser(
                new ObjectMapper(), Validation.buildDefaultValidatorFactory().getValidator());
        MqttTelemetryMessageProcessor messageProcessor = new MqttTelemetryMessageProcessor(
                iotService, metrics, payloadParser);

        assertThatThrownBy(() -> new MqttTelemetryListener(
                metrics, messageProcessor, ingressGate(), "tcp://broker.example:1883", false,
                "test-client", "agricore/telemetry/+/reading", 1, 1, 1, "user", "password"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must use TLS");
    }

    private static MqttMessage message(String payload) {
        MqttMessage message = new MqttMessage(payload.getBytes());
        message.setQos(1);
        return message;
    }

    private static MqttDeviceIngressGate ingressGate() {
        return new MqttDeviceIngressGate(100, 100, 4, 100, Duration.ofMinutes(1));
    }
}
