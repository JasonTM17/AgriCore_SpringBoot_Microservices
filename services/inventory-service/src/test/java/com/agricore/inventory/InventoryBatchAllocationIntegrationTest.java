package com.agricore.inventory;

import com.agricore.inventory.api.request.CreateItemRequest;
import com.agricore.inventory.api.request.CreateWarehouseRequest;
import com.agricore.inventory.api.request.ReserveStockRequest;
import com.agricore.inventory.api.request.StockInRequest;
import com.agricore.inventory.api.request.StockOutRequest;
import com.agricore.inventory.api.response.InventoryItemResponse;
import com.agricore.inventory.application.service.InventoryApplicationService;
import com.agricore.inventory.domain.exception.InventoryException;
import com.agricore.inventory.infrastructure.persistence.InventoryBatchJpaRepository;
import com.agricore.inventory.infrastructure.persistence.InventoryItemJpaRepository;
import com.agricore.inventory.infrastructure.persistence.InventoryReservationAllocationJpaRepository;
import com.agricore.inventory.infrastructure.persistence.entity.InventoryBatchEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class InventoryBatchAllocationIntegrationTest {

    @Autowired
    private InventoryApplicationService inventoryService;
    @Autowired
    private InventoryBatchJpaRepository batchRepository;
    @Autowired
    private InventoryReservationAllocationJpaRepository allocationRepository;
    @Autowired
    private InventoryItemJpaRepository itemRepository;

    @Test
    void reservationsUseFefoThenFifoAndReleaseAndConfirmMaintainBatchBalances() {
        InventoryItemResponse item = createItem("FEFO");
        Instant now = Instant.now();
        Instant earliestExpiry = now.plus(10, ChronoUnit.DAYS);
        Instant laterExpiry = now.plus(30, ChronoUnit.DAYS);

        stockIn(item, "LOT-EARLIEST", earliestExpiry, "4.000");
        stockIn(item, "LOT-LATER", laterExpiry, "6.000");

        var reservation = inventoryService.reserve(new ReserveStockRequest(
                item.id(), new BigDecimal("7.000"), "SalesOrder", "FEFO-" + UUID.randomUUID()
        ));
        var allocations = allocationRepository
                .findAllByReservationIdOrderByCreatedAtAscIdAsc(reservation.id());

        assertThat(allocations).hasSize(2);
        assertThat(batchRepository.findById(allocations.get(0).getBatchId()).orElseThrow().getLotCode())
                .isEqualTo("LOT-EARLIEST");
        assertThat(allocations.get(0).getQuantity()).isEqualByComparingTo("4.000");
        assertThat(allocations.get(1).getQuantity()).isEqualByComparingTo("3.000");

        inventoryService.release(reservation.id());
        assertThat(batchRepository.findByInventoryItemIdAndLotCode(item.id(), "LOT-EARLIEST")
                .orElseThrow().getReservedQuantity()).isEqualByComparingTo("0.000");

        var confirmedReservation = inventoryService.reserve(new ReserveStockRequest(
                item.id(), new BigDecimal("7.000"), "SalesOrder", "FEFO-CONFIRM-" + UUID.randomUUID()
        ));
        inventoryService.confirm(confirmedReservation.id());

        InventoryItemResponse afterConfirm = inventoryService.getItem(item.id());
        assertThat(afterConfirm.onHandQuantity()).isEqualByComparingTo("3.000");
        assertThat(afterConfirm.reservedQuantity()).isEqualByComparingTo("0.000");
        assertThat(batchRepository.findByInventoryItemIdAndLotCode(item.id(), "LOT-EARLIEST")
                .orElseThrow().getQuantity()).isEqualByComparingTo("0.000");
        assertThat(batchRepository.findByInventoryItemIdAndLotCode(item.id(), "LOT-LATER")
                .orElseThrow().getQuantity()).isEqualByComparingTo("3.000");
    }

    @Test
    void manualStockOutConsumesTheEarliestUnreservedBatch() {
        InventoryItemResponse item = createItem("STOCK-OUT");
        Instant now = Instant.now();
        stockIn(item, "LOT-EARLIEST", now.plus(5, ChronoUnit.DAYS), "2.000");
        stockIn(item, "LOT-LATER", now.plus(20, ChronoUnit.DAYS), "5.000");

        inventoryService.stockOut(new StockOutRequest(
                item.id(), new BigDecimal("3.000"), "Dispatch", "FEFO-OUT-" + UUID.randomUUID(), null
        ));

        assertThat(batchRepository.findByInventoryItemIdAndLotCode(item.id(), "LOT-EARLIEST")
                .orElseThrow().getQuantity()).isEqualByComparingTo("0.000");
        assertThat(batchRepository.findByInventoryItemIdAndLotCode(item.id(), "LOT-LATER")
                .orElseThrow().getQuantity()).isEqualByComparingTo("4.000");
    }

    @Test
    void expiredAvailableStockCannotBeReservedOrDispatched() {
        InventoryItemResponse item = createItem("EXPIRED");
        InventoryBatchEntity expired = new InventoryBatchEntity();
        expired.setId(UUID.randomUUID());
        expired.setInventoryItemId(item.id());
        expired.setLotCode("LOT-EXPIRED");
        expired.setReceivedAt(Instant.now().minus(30, ChronoUnit.DAYS));
        expired.setExpiresAt(Instant.now().minus(1, ChronoUnit.MINUTES));
        expired.setQuantity(new BigDecimal("2.000"));
        expired.setReservedQuantity(BigDecimal.ZERO);
        expired.setCreatedAt(expired.getReceivedAt());
        expired.setUpdatedAt(expired.getReceivedAt());
        batchRepository.saveAndFlush(expired);

        var entity = itemRepository.findById(item.id()).orElseThrow();
        entity.setOnHandQuantity(new BigDecimal("2.000"));
        itemRepository.saveAndFlush(entity);

        assertThatThrownBy(() -> inventoryService.reserve(new ReserveStockRequest(
                item.id(), BigDecimal.ONE, "SalesOrder", "EXPIRED-RESERVE-" + UUID.randomUUID()
        )))
                .isInstanceOf(InventoryException.class)
                .hasMessageContaining("Available stock is expired");
        assertThatThrownBy(() -> inventoryService.stockOut(new StockOutRequest(
                item.id(), BigDecimal.ONE, "Dispatch", "EXPIRED-OUT-" + UUID.randomUUID(), null
        )))
                .isInstanceOf(InventoryException.class)
                .hasMessageContaining("Available stock is expired");
    }

    @Test
    void stockInRejectsPastExpiryAndReplaysReferenceWithoutDuplicatingStock() {
        InventoryItemResponse item = createItem("IDEMPOTENT");
        String referenceId = "STOCK-IN-" + UUID.randomUUID();
        Instant now = Instant.now();

        assertThatThrownBy(() -> inventoryService.stockIn(new StockInRequest(
                item.id(),
                BigDecimal.ONE,
                "PurchaseOrder",
                referenceId,
                null,
                "LOT-INVALID",
                now.minusSeconds(1)
        )))
                .isInstanceOf(InventoryException.class)
                .hasMessageContaining("Expiry must be after the stock receipt time");

        inventoryService.stockIn(new StockInRequest(
                item.id(),
                new BigDecimal("2.000"),
                "PurchaseOrder",
                referenceId,
                null,
                "LOT-IDEMPOTENT",
                now.plus(10, ChronoUnit.DAYS)
        ));
        inventoryService.stockIn(new StockInRequest(
                item.id(),
                new BigDecimal("2.000"),
                "PurchaseOrder",
                referenceId,
                null,
                "LOT-IDEMPOTENT",
                now.plus(20, ChronoUnit.DAYS)
        ));

        assertThat(inventoryService.getItem(item.id()).onHandQuantity())
                .isEqualByComparingTo("2.000");
        assertThatThrownBy(() -> inventoryService.stockIn(new StockInRequest(
                item.id(),
                new BigDecimal("3.000"),
                "PurchaseOrder",
                referenceId,
                null,
                "LOT-IDEMPOTENT",
                now.plus(10, ChronoUnit.DAYS)
        )))
                .isInstanceOf(InventoryException.class)
                .hasMessageContaining("different quantity");
    }

    private void stockIn(
            InventoryItemResponse item,
            String lotCode,
            Instant expiresAt,
            String quantity
    ) {
        inventoryService.stockIn(new StockInRequest(
                item.id(),
                new BigDecimal(quantity),
                "PurchaseOrder",
                lotCode + "-" + UUID.randomUUID(),
                null,
                lotCode,
                expiresAt
        ));
    }

    private InventoryItemResponse createItem(String prefix) {
        var warehouse = inventoryService.createWarehouse(new CreateWarehouseRequest(
                UUID.randomUUID(),
                "WH-" + prefix + "-" + UUID.randomUUID(),
                "Batch allocation test warehouse"
        ));
        return inventoryService.createItem(new CreateItemRequest(
                warehouse.id(),
                "SKU-" + prefix + "-" + UUID.randomUUID(),
                "Batch allocation test item",
                "PRODUCE",
                "KG"
        ));
    }
}
