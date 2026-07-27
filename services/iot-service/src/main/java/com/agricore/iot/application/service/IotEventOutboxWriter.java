package com.agricore.iot.application.service;

import com.agricore.common.event.EventTypes;
import com.agricore.iot.domain.exception.IotException;
import com.agricore.iot.infrastructure.persistence.OutboxJpaRepository;
import com.agricore.iot.infrastructure.persistence.entity.DeviceEntity;
import com.agricore.iot.infrastructure.persistence.entity.OutboxEventEntity;
import com.agricore.iot.infrastructure.persistence.entity.SensorAlertEntity;
import com.agricore.iot.infrastructure.persistence.entity.SensorReadingEntity;
import com.agricore.iot.infrastructure.persistence.entity.ThresholdRuleEntity;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Component
public class IotEventOutboxWriter {

    private static final String TOPIC = "agricore.iot.events";

    private final OutboxJpaRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public IotEventOutboxWriter(OutboxJpaRepository outboxRepository, ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void sensorReadingReceived(DeviceEntity device, SensorReadingEntity reading) {
        ObjectNode payload = readingPayload(device, reading);
        enqueue(EventTypes.SENSOR_READING_RECEIVED, "SensorReading", reading.getId(), payload, reading.getCreatedAt());
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void sensorThresholdExceeded(
            DeviceEntity device,
            SensorReadingEntity reading,
            ThresholdRuleEntity rule,
            SensorAlertEntity alert
    ) {
        ObjectNode payload = readingPayload(device, reading);
        payload.put("alertId", alert.getId().toString());
        payload.put("severity", alert.getSeverity());
        payload.put("ruleVersion", alert.getRuleVersion());
        if (rule.getMinValue() != null) {
            payload.put("minValue", rule.getMinValue());
        }
        if (rule.getMaxValue() != null) {
            payload.put("maxValue", rule.getMaxValue());
        }
        payload.put("message", alert.getMessage());
        payload.put("detectedAt", alert.getCreatedAt().toString());
        enqueue(EventTypes.SENSOR_THRESHOLD_EXCEEDED, "SensorAlert", alert.getId(), payload, alert.getCreatedAt());
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void deviceOfflineDetected(
            DeviceEntity device,
            Instant lastActivityAt,
            Instant detectedAt,
            Duration offlineAfter
    ) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("deviceId", device.getId().toString());
        payload.put("deviceCode", device.getDeviceCode());
        payload.put("plotId", device.getPlotId().toString());
        payload.put("deviceName", device.getName());
        payload.put("lastActivityAt", lastActivityAt.toString());
        payload.put("detectedAt", detectedAt.toString());
        payload.put("offlineAfterSeconds", offlineAfter.toSeconds());
        enqueue(EventTypes.DEVICE_OFFLINE_DETECTED, "Device", device.getId(), payload, detectedAt);
    }

    private ObjectNode readingPayload(DeviceEntity device, SensorReadingEntity reading) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("readingId", reading.getId().toString());
        payload.put("deviceId", device.getId().toString());
        payload.put("deviceCode", device.getDeviceCode());
        payload.put("plotId", device.getPlotId().toString());
        payload.put("metricType", reading.getMetricType());
        payload.put("metricValue", reading.getMetricValue());
        payload.put("unit", reading.getUnit());
        payload.put("recordedAt", reading.getRecordedAt().toString());
        return payload;
    }

    private void enqueue(
            String eventType,
            String aggregateType,
            UUID aggregateId,
            ObjectNode payload,
            Instant occurredAt
    ) {
        UUID eventId = UUID.randomUUID();
        ObjectNode envelope = objectMapper.createObjectNode();
        envelope.put("eventId", eventId.toString());
        envelope.put("eventType", eventType);
        envelope.put("eventVersion", 1);
        envelope.put("occurredAt", occurredAt.toString());
        envelope.put("producer", "iot-service");
        envelope.set("payload", payload);
        try {
            outboxRepository.save(OutboxEventEntity.create(
                    eventId,
                    aggregateType,
                    aggregateId.toString(),
                    eventType,
                    TOPIC,
                    objectMapper.writeValueAsString(envelope)
            ));
        } catch (JsonProcessingException exception) {
            throw new IotException("OUTBOX_WRITE_FAILED", "Failed to write outbox event", 500);
        }
    }
}
