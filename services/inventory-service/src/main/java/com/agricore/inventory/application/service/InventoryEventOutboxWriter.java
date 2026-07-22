package com.agricore.inventory.application.service;

import com.agricore.common.event.EventTypes;
import com.agricore.inventory.api.request.ReserveStockRequest;
import com.agricore.inventory.infrastructure.persistence.OutboxJpaRepository;
import com.agricore.inventory.infrastructure.persistence.entity.InventoryItemEntity;
import com.agricore.inventory.infrastructure.persistence.entity.InventoryReservationEntity;
import com.agricore.inventory.infrastructure.persistence.entity.OutboxEventEntity;
import com.agricore.inventory.infrastructure.persistence.entity.StockMovementEntity;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Component
public class InventoryEventOutboxWriter {

    private static final String TOPIC = "agricore.inventory.events";

    private final OutboxJpaRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public InventoryEventOutboxWriter(OutboxJpaRepository outboxRepository, ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void stockAdded(InventoryItemEntity item, StockMovementEntity movement) {
        Map<String, Object> payload = movementPayload(item, movement);
        payload.put("onHandQuantity", item.getOnHandQuantity());
        payload.put("availableQuantity", item.availableQuantity());
        enqueue(EventTypes.STOCK_ADDED, item.getId(), payload);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void inventoryReserved(InventoryItemEntity item, InventoryReservationEntity reservation) {
        Map<String, Object> payload = reservationPayload(item, reservation);
        payload.put("reservedQuantity", item.getReservedQuantity());
        payload.put("availableQuantity", item.availableQuantity());
        enqueue(EventTypes.INVENTORY_RESERVED, item.getId(), payload);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void inventoryReservationFailed(InventoryItemEntity item, ReserveStockRequest request) {
        Map<String, Object> payload = itemPayload(item);
        payload.put("requestedQuantity", request.quantity());
        payload.put("availableQuantity", item.availableQuantity());
        payload.put("referenceType", request.referenceType().trim());
        payload.put("referenceId", request.referenceId().trim());
        payload.put("reasonCode", "INSUFFICIENT_STOCK");
        enqueue(EventTypes.INVENTORY_RESERVATION_FAILED, item.getId(), payload);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void inventoryReleased(InventoryItemEntity item, InventoryReservationEntity reservation) {
        Map<String, Object> payload = reservationPayload(item, reservation);
        payload.put("reservedQuantity", item.getReservedQuantity());
        payload.put("availableQuantity", item.availableQuantity());
        enqueue(EventTypes.INVENTORY_RELEASED, item.getId(), payload);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void stockDeducted(
            InventoryItemEntity item,
            StockMovementEntity movement,
            UUID reservationId
    ) {
        Map<String, Object> payload = movementPayload(item, movement);
        payload.put("onHandQuantity", item.getOnHandQuantity());
        payload.put("availableQuantity", item.availableQuantity());
        if (reservationId != null) {
            payload.put("reservationId", reservationId);
        }
        enqueue(EventTypes.STOCK_DEDUCTED, item.getId(), payload);
    }

    private Map<String, Object> movementPayload(InventoryItemEntity item, StockMovementEntity movement) {
        Map<String, Object> payload = itemPayload(item);
        payload.put("movementId", movement.getId());
        payload.put("quantity", movement.getQuantity());
        payload.put("referenceType", movement.getReferenceType());
        payload.put("referenceId", movement.getReferenceId());
        return payload;
    }

    private Map<String, Object> reservationPayload(
            InventoryItemEntity item,
            InventoryReservationEntity reservation
    ) {
        Map<String, Object> payload = itemPayload(item);
        payload.put("reservationId", reservation.getId());
        payload.put("quantity", reservation.getQuantity());
        payload.put("referenceType", reservation.getReferenceType());
        payload.put("referenceId", reservation.getReferenceId());
        return payload;
    }

    private Map<String, Object> itemPayload(InventoryItemEntity item) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("inventoryItemId", item.getId());
        payload.put("warehouseId", item.getWarehouseId());
        payload.put("sku", item.getSku());
        payload.put("itemType", item.getItemType());
        payload.put("unit", item.getUnit());
        return payload;
    }

    private void enqueue(String eventType, UUID itemId, Map<String, Object> payload) {
        UUID eventId = UUID.randomUUID();
        ObjectNode envelope = objectMapper.createObjectNode();
        envelope.put("eventId", eventId.toString());
        envelope.put("eventType", eventType);
        envelope.put("eventVersion", 1);
        envelope.put("occurredAt", Instant.now().toString());
        envelope.put("producer", "inventory-service");
        envelope.set("payload", objectMapper.valueToTree(payload));

        try {
            outboxRepository.save(OutboxEventEntity.create(
                    eventId,
                    "InventoryItem",
                    itemId.toString(),
                    eventType,
                    TOPIC,
                    objectMapper.writeValueAsString(envelope)
            ));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot serialize inventory event " + eventType, exception);
        }
    }
}
