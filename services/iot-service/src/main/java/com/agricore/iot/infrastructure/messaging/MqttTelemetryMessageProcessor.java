package com.agricore.iot.infrastructure.messaging;

import com.agricore.iot.api.request.IngestReadingRequest;
import com.agricore.iot.application.service.IotApplicationService;
import com.agricore.iot.application.service.IotMetrics;
import com.agricore.iot.domain.exception.IotException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class MqttTelemetryMessageProcessor {

    private static final Logger log = LoggerFactory.getLogger(MqttTelemetryMessageProcessor.class);
    private static final int MAX_PAYLOAD_BYTES = 16 * 1024;
    private static final int PROCESSING_ATTEMPTS = 3;

    private final IotApplicationService iotApplicationService;
    private final IotMetrics metrics;
    private final MqttTelemetryPayloadParser payloadParser;

    public MqttTelemetryMessageProcessor(
            IotApplicationService iotApplicationService,
            IotMetrics metrics,
            MqttTelemetryPayloadParser payloadParser
    ) {
        this.iotApplicationService = iotApplicationService;
        this.metrics = metrics;
        this.payloadParser = payloadParser;
    }

    public Disposition process(String topicName, MqttMessage message) {
        if (message.getQos() != 1) {
            metrics.recordMqttOutcome("rejected");
            log.warn("mqtt_telemetry_rejected reason=qos_mismatch");
            return Disposition.ACKNOWLEDGE;
        }
        if (message.getPayload().length > MAX_PAYLOAD_BYTES) {
            metrics.recordMqttOutcome("oversized");
            log.warn("mqtt_telemetry_rejected reason=payload_too_large");
            return Disposition.ACKNOWLEDGE;
        }

        IngestReadingRequest request;
        try {
            request = payloadParser.parse(topicName, message.getPayload());
        } catch (Exception exception) {
            metrics.recordMqttOutcome("rejected");
            log.warn("mqtt_telemetry_rejected reason={}",
                    exception.getClass().getSimpleName());
            return Disposition.ACKNOWLEDGE;
        }

        for (int attempt = 1; attempt <= PROCESSING_ATTEMPTS; attempt++) {
            try {
                iotApplicationService.ingestFromMqtt(request);
                metrics.recordMqttOutcome("accepted");
                return Disposition.ACKNOWLEDGE;
            } catch (IotException exception) {
                if (exception.getHttpStatus() < 500) {
                    metrics.recordMqttOutcome("rejected");
                    log.warn("mqtt_telemetry_rejected reasonCode={}", exception.getCode());
                    return Disposition.ACKNOWLEDGE;
                }
                if (!pauseBeforeRetry(attempt)) {
                    break;
                }
            } catch (RuntimeException exception) {
                if (!pauseBeforeRetry(attempt)) {
                    break;
                }
            }
        }

        metrics.recordMqttOutcome("processing_failed");
        log.error("mqtt_telemetry_processing_failed attempts={}", PROCESSING_ATTEMPTS);
        return Disposition.REDELIVER;
    }

    private static boolean pauseBeforeRetry(int attempt) {
        if (attempt >= PROCESSING_ATTEMPTS) {
            return false;
        }
        try {
            Thread.sleep(100L << (attempt - 1));
            return true;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    public enum Disposition {
        ACKNOWLEDGE,
        REDELIVER
    }
}
