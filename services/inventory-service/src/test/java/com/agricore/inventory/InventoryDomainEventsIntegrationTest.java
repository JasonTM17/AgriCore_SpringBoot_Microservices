package com.agricore.inventory;

import com.agricore.common.event.EventTypes;
import com.agricore.inventory.api.request.CreateItemRequest;
import com.agricore.inventory.api.request.CreateWarehouseRequest;
import com.agricore.inventory.api.request.ReserveStockRequest;
import com.agricore.inventory.api.request.StockInRequest;
import com.agricore.inventory.api.request.StockOutRequest;
import com.agricore.inventory.api.response.InventoryItemResponse;
import com.agricore.inventory.application.service.InventoryApplicationService;
import com.agricore.inventory.domain.exception.InventoryException;
import com.agricore.inventory.domain.model.MovementType;
import com.agricore.inventory.infrastructure.persistence.InventoryItemJpaRepository;
import com.agricore.inventory.infrastructure.persistence.InventoryReservationJpaRepository;
import com.agricore.inventory.infrastructure.persistence.OutboxJpaRepository;
import com.agricore.inventory.infrastructure.persistence.ProcessedEventJpaRepository;
import com.agricore.inventory.infrastructure.persistence.StockMovementJpaRepository;
import com.agricore.inventory.infrastructure.persistence.WarehouseJpaRepository;
import com.agricore.inventory.infrastructure.persistence.entity.OutboxEventEntity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class InventoryDomainEventsIntegrationTest {

    @Autowired
    private InventoryApplicationService inventoryService;
    @Autowired
    private WarehouseJpaRepository warehouseRepository;
    @Autowired
    private InventoryItemJpaRepository itemRepository;
    @Autowired
    private StockMovementJpaRepository movementRepository;
    @Autowired
    private InventoryReservationJpaRepository reservationRepository;
    @Autowired
    private ProcessedEventJpaRepository processedEventRepository;
    @Autowired
    private OutboxJpaRepository outboxRepository;
    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void cleanDatabase() {
        outboxRepository.deleteAllInBatch();
        processedEventRepository.deleteAllInBatch();
        reservationRepository.deleteAllInBatch();
        movementRepository.deleteAllInBatch();
        itemRepository.deleteAllInBatch();
        warehouseRepository.deleteAllInBatch();
    }

    @Test
    void stockInPersistsContractEnvelopeInTheSameTransaction() throws Exception {
        InventoryItemResponse item = createItem("MATERIAL");

        InventoryItemResponse stocked = inventoryService.stockIn(new StockInRequest(
                item.id(), new BigDecimal("12.500"), "PurchaseOrder", "PO-100", "Supplier receipt"
        ));

        OutboxEventEntity event = onlyEvent(EventTypes.STOCK_ADDED);
        JsonNode envelope = objectMapper.readTree(event.getPayload());
        assertThat(event.getId().toString()).isEqualTo(envelope.path("eventId").asText());
        assertThat(envelope.path("eventType").asText()).isEqualTo(EventTypes.STOCK_ADDED);
        assertThat(envelope.path("producer").asText()).isEqualTo("inventory-service");
        assertThat(envelope.path("payload").path("inventoryItemId").asText()).isEqualTo(item.id().toString());
        assertThat(envelope.path("payload").path("movementId").asText()).isNotBlank();
        assertThat(envelope.path("payload").path("onHandQuantity").decimalValue())
                .isEqualByComparingTo(stocked.onHandQuantity());
        assertThat(event.getAggregateId()).isEqualTo(item.id().toString());
        assertThat(event.getTopic()).isEqualTo("agricore.inventory.events");
    }

    @Test
    void reservationLifecycleEmitsSuccessReleaseAndDeductionOnce() {
        InventoryItemResponse item = createStockedItem("PRODUCE", "20.000");
        outboxRepository.deleteAllInBatch();

        var releasedReservation = inventoryService.reserve(new ReserveStockRequest(
                item.id(), new BigDecimal("4.000"), "SalesOrder", "SO-RELEASE"
        ));
        inventoryService.release(releasedReservation.id());
        inventoryService.release(releasedReservation.id());

        var fulfilledReservation = inventoryService.reserve(new ReserveStockRequest(
                item.id(), new BigDecimal("6.000"), "SalesOrder", "SO-CONFIRM"
        ));
        inventoryService.confirm(fulfilledReservation.id());
        inventoryService.confirm(fulfilledReservation.id());

        assertEventCount(EventTypes.INVENTORY_RESERVED, 2);
        assertEventCount(EventTypes.INVENTORY_RELEASED, 1);
        assertEventCount(EventTypes.STOCK_DEDUCTED, 1);
        assertThat(inventoryService.getItem(item.id()).onHandQuantity()).isEqualByComparingTo("14.000");
    }

    @Test
    void insufficientReservationCommitsFailureEventWhileBusinessTransactionRollsBack() throws Exception {
        InventoryItemResponse item = createStockedItem("MATERIAL", "2.000");
        outboxRepository.deleteAllInBatch();

        ReserveStockRequest request = new ReserveStockRequest(
                item.id(), new BigDecimal("3.000"), "WorkTask", "TASK-9"
        );
        assertThatThrownBy(() -> inventoryService.reserve(request))
                .isInstanceOf(InventoryException.class)
                .hasMessage("Not enough available stock");

        OutboxEventEntity event = onlyEvent(EventTypes.INVENTORY_RESERVATION_FAILED);
        JsonNode payload = objectMapper.readTree(event.getPayload()).path("payload");
        assertThat(payload.path("requestedQuantity").decimalValue()).isEqualByComparingTo("3.000");
        assertThat(payload.path("availableQuantity").decimalValue()).isEqualByComparingTo("2.000");
        assertThat(payload.path("reasonCode").asText()).isEqualTo("INSUFFICIENT_STOCK");
        assertThat(reservationRepository.count()).isZero();
        assertThat(inventoryService.getItem(item.id()).reservedQuantity()).isEqualByComparingTo("0.000");
    }

    @Test
    void stockOutUsesReferenceAsIdempotencyKeyAndProtectsReservedStock() {
        InventoryItemResponse item = createStockedItem("MATERIAL", "10.000");
        outboxRepository.deleteAllInBatch();

        StockOutRequest request = new StockOutRequest(
                item.id(), new BigDecimal("3.000"), "WorkTask", "TASK-42", "Applied fertilizer"
        );
        InventoryItemResponse first = inventoryService.stockOut(request);
        InventoryItemResponse duplicate = inventoryService.stockOut(request);

        assertThat(first.onHandQuantity()).isEqualByComparingTo("7.000");
        assertThat(duplicate.onHandQuantity()).isEqualByComparingTo("7.000");
        assertThat(movementRepository.findAll()).filteredOn(
                movement -> movement.getMovementType() == MovementType.STOCK_OUT
        ).hasSize(1);
        assertEventCount(EventTypes.STOCK_DEDUCTED, 1);

        assertThatThrownBy(() -> inventoryService.stockOut(new StockOutRequest(
                item.id(), new BigDecimal("4.000"), "WorkTask", "TASK-42", null
        ))).isInstanceOf(InventoryException.class)
                .hasMessage("Stock-out reference was already used with a different quantity");

        inventoryService.reserve(new ReserveStockRequest(
                item.id(), new BigDecimal("6.000"), "SalesOrder", "SO-PROTECTED"
        ));
        assertThatThrownBy(() -> inventoryService.stockOut(new StockOutRequest(
                item.id(), new BigDecimal("2.000"), "WorkTask", "TASK-43", null
        ))).isInstanceOf(InventoryException.class).hasMessage("Not enough available stock");
        assertThat(inventoryService.getItem(item.id()).onHandQuantity()).isEqualByComparingTo("7.000");
    }

    private InventoryItemResponse createStockedItem(String itemType, String quantity) {
        InventoryItemResponse item = createItem(itemType);
        return inventoryService.stockIn(new StockInRequest(
                item.id(), new BigDecimal(quantity), "Seed", UUID.randomUUID().toString(), null
        ));
    }

    private InventoryItemResponse createItem(String itemType) {
        var warehouse = inventoryService.createWarehouse(new CreateWarehouseRequest(
                UUID.randomUUID(), "WH-" + UUID.randomUUID(), "Event Test Warehouse"
        ));
        return inventoryService.createItem(new CreateItemRequest(
                warehouse.id(), "SKU-" + UUID.randomUUID(), "Event Test Item", itemType, "KG"
        ));
    }

    private OutboxEventEntity onlyEvent(String eventType) {
        List<OutboxEventEntity> events = outboxRepository.findAll().stream()
                .filter(event -> eventType.equals(event.getEventType()))
                .toList();
        assertThat(events).hasSize(1);
        return events.getFirst();
    }

    private void assertEventCount(String eventType, int expected) {
        assertThat(outboxRepository.findAll()).filteredOn(
                event -> eventType.equals(event.getEventType())
        ).hasSize(expected);
    }
}
