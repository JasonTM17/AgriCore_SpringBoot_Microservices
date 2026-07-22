package com.agricore.sales.application.service;

import com.agricore.common.event.EventTypes;
import com.agricore.sales.infrastructure.persistence.OutboxJpaRepository;
import com.agricore.sales.infrastructure.persistence.entity.OutboxEventEntity;
import com.agricore.sales.infrastructure.persistence.entity.SalesOrderEntity;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Component
public class SalesEventOutboxWriter {

    private static final String TOPIC = "agricore.sales.events";

    private final OutboxJpaRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public SalesEventOutboxWriter(OutboxJpaRepository outboxRepository, ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void salesOrderCreated(SalesOrderEntity order) {
        ObjectNode payload = commonPayload(order);
        payload.put("createdAt", order.getCreatedAt().toString());
        enqueue(EventTypes.SALES_ORDER_CREATED, order, order.getCreatedAt(), payload);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void salesOrderConfirmed(SalesOrderEntity order) {
        ObjectNode payload = commonPayload(order);
        payload.put("reservationId", order.getReservationId().toString());
        payload.put("confirmedAt", order.getUpdatedAt().toString());
        enqueue(EventTypes.SALES_ORDER_CONFIRMED, order, order.getUpdatedAt(), payload);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void salesOrderCancelled(SalesOrderEntity order, String reasonCode) {
        ObjectNode payload = commonPayload(order);
        payload.put("finalStatus", order.getStatus().name());
        payload.put("reasonCode", reasonCode);
        payload.put("reason", order.getFailureReason());
        payload.put("cancelledAt", order.getUpdatedAt().toString());
        if (order.getReservationId() != null) {
            payload.put("reservationId", order.getReservationId().toString());
        }
        enqueue(EventTypes.SALES_ORDER_CANCELLED, order, order.getUpdatedAt(), payload);
    }

    private ObjectNode commonPayload(SalesOrderEntity order) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("salesOrderId", order.getId().toString());
        payload.put("orderNumber", order.getOrderNumber());
        payload.put("customerId", order.getCustomerId().toString());
        payload.put("inventoryItemId", order.getInventoryItemId().toString());
        payload.put("quantity", order.getQuantity());
        payload.put("status", order.getStatus().name());
        return payload;
    }

    private void enqueue(
            String eventType,
            SalesOrderEntity order,
            Instant occurredAt,
            ObjectNode payload
    ) {
        UUID eventId = UUID.randomUUID();
        ObjectNode envelope = objectMapper.createObjectNode();
        envelope.put("eventId", eventId.toString());
        envelope.put("eventType", eventType);
        envelope.put("eventVersion", 1);
        envelope.put("occurredAt", occurredAt.toString());
        envelope.put("correlationId", order.getCorrelationId().toString());
        envelope.put("producer", "sales-service");
        envelope.set("payload", payload);

        try {
            outboxRepository.save(OutboxEventEntity.create(
                    eventId,
                    "SalesOrder",
                    order.getId().toString(),
                    eventType,
                    TOPIC,
                    objectMapper.writeValueAsString(envelope)
            ));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot serialize sales event " + eventType, exception);
        }
    }
}
