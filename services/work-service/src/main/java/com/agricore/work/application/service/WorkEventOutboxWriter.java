package com.agricore.work.application.service;

import com.agricore.common.event.EventTypes;
import com.agricore.work.domain.exception.WorkException;
import com.agricore.work.infrastructure.persistence.OutboxJpaRepository;
import com.agricore.work.infrastructure.persistence.entity.OutboxEventEntity;
import com.agricore.work.infrastructure.persistence.entity.MaterialUsageEntity;
import com.agricore.work.infrastructure.persistence.entity.WorkTaskEntity;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Component
public class WorkEventOutboxWriter {

    private static final String TOPIC = "agricore.work.events";

    private final OutboxJpaRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public WorkEventOutboxWriter(OutboxJpaRepository outboxRepository, ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void workTask(String eventType, WorkTaskEntity task) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("taskId", task.getId().toString());
        payload.put("code", task.getCode());
        payload.put("cropCycleId", task.getCropCycleId().toString());
        payload.put("plotId", task.getPlotId().toString());
        payload.put("taskType", task.getTaskType().name());
        payload.put("status", task.getStatus().name());
        if (task.getAssignedEmployeeId() != null) {
            payload.put("assignedEmployeeId", task.getAssignedEmployeeId().toString());
        }
        enqueue(eventType, "WorkTask", task.getId(), payload);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void materialConsumed(WorkTaskEntity task, MaterialUsageEntity usage) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("materialUsageId", usage.getId().toString());
        payload.put("taskId", task.getId().toString());
        payload.put("cropCycleId", task.getCropCycleId().toString());
        payload.put("plotId", task.getPlotId().toString());
        payload.put("inventoryItemId", usage.getInventoryItemId().toString());
        payload.put("quantity", usage.getQuantity());
        payload.put("unit", usage.getUnit());
        payload.put("referenceType", "WorkTask");
        payload.put("referenceId", usage.getInventoryReferenceId());
        payload.put("consumedAt", usage.getConsumedAt().toString());
        enqueue(EventTypes.MATERIAL_CONSUMED, "MaterialUsage", usage.getId(), payload);
    }

    private void enqueue(String eventType, String aggregateType, UUID aggregateId, ObjectNode payload) {
        UUID eventId = UUID.randomUUID();
        ObjectNode envelope = objectMapper.createObjectNode();
        envelope.put("eventId", eventId.toString());
        envelope.put("eventType", eventType);
        envelope.put("eventVersion", 1);
        envelope.put("occurredAt", Instant.now().toString());
        envelope.put("producer", "work-service");
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
            throw new WorkException("OUTBOX_WRITE_FAILED", "Failed to write outbox event", 500);
        }
    }
}
