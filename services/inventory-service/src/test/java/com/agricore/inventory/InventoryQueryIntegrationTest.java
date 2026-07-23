package com.agricore.inventory;

import com.agricore.inventory.api.request.CreateItemRequest;
import com.agricore.inventory.api.request.CreateWarehouseRequest;
import com.agricore.inventory.api.request.StockInRequest;
import com.agricore.inventory.api.response.InventoryItemResponse;
import com.agricore.inventory.application.service.InventoryApplicationService;
import com.agricore.inventory.infrastructure.persistence.InventoryItemJpaRepository;
import com.agricore.inventory.infrastructure.persistence.StockMovementJpaRepository;
import com.agricore.inventory.infrastructure.persistence.entity.InventoryItemEntity;
import com.agricore.common.api.PageResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class InventoryQueryIntegrationTest {

    @Autowired
    private InventoryApplicationService inventoryService;
    @Autowired
    private InventoryItemJpaRepository itemRepository;
    @Autowired
    private StockMovementJpaRepository movementRepository;

    @Test
    void listQueriesReturnTruthfulPagedItemsAndMovements() {
        var warehouse = inventoryService.createWarehouse(new CreateWarehouseRequest(
                UUID.randomUUID(),
                "WH-QUERY-" + UUID.randomUUID(),
                "Query warehouse"
        ));
        InventoryItemResponse item = inventoryService.createItem(new CreateItemRequest(
                warehouse.id(),
                "SKU-QUERY-" + UUID.randomUUID(),
                "Query item",
                "MATERIAL",
                "KG"
        ));
        inventoryService.stockIn(new StockInRequest(
                item.id(),
                new BigDecimal("4.500"),
                "PurchaseOrder",
                "QUERY-" + UUID.randomUUID(),
                "query stock",
                "QUERY-LOT",
                Instant.now().plusSeconds(3600)
        ));

        PageResponse<InventoryItemResponse> items = inventoryService.listItems(
                warehouse.id(),
                PageRequest.of(0, 10, Sort.by("sku").ascending())
        );
        assertThat(items.content()).extracting(InventoryItemResponse::id).contains(item.id());
        assertThat(items.totalElements()).isEqualTo(1);
        assertThat(items.first()).isTrue();
        assertThat(items.last()).isTrue();

        PageResponse<com.agricore.inventory.api.response.StockMovementResponse> movements =
                inventoryService.listMovements(
                        item.id(),
                        PageRequest.of(0, 10, Sort.by("createdAt").ascending())
                );
        assertThat(movements.content()).hasSize(1);
        var movement = movements.content().getFirst();
        assertThat(movement.itemId()).isEqualTo(item.id());
        assertThat(movement.batchId()).isNotNull();
        assertThat(movement.type()).isEqualTo("STOCK_IN");
        assertThat(movement.quantity()).isEqualByComparingTo("4.500");
        assertThat(movement.referenceType()).isEqualTo("PurchaseOrder");
        assertThat(movement.referenceId()).startsWith("QUERY-");
        assertThat(movement.note()).isEqualTo("query stock");
    }

    @Test
    void stockInReferenceReplayLeavesOneMovementAndAggregateInvariantIntact() {
        var warehouse = inventoryService.createWarehouse(new CreateWarehouseRequest(
                UUID.randomUUID(),
                "WH-REPLAY-" + UUID.randomUUID(),
                "Replay warehouse"
        ));
        InventoryItemResponse item = inventoryService.createItem(new CreateItemRequest(
                warehouse.id(),
                "SKU-REPLAY-" + UUID.randomUUID(),
                "Replay item",
                "MATERIAL",
                "KG"
        ));
        String referenceId = "REPLAY-" + UUID.randomUUID();
        StockInRequest request = new StockInRequest(
                item.id(),
                new BigDecimal("2.000"),
                "PurchaseOrder",
                referenceId,
                null,
                "REPLAY-LOT",
                Instant.now().plusSeconds(3600)
        );

        inventoryService.stockIn(request);
        inventoryService.stockIn(request);

        assertThat(inventoryService.getItem(item.id()).onHandQuantity())
                .isEqualByComparingTo("2.000");
        assertThat(movementRepository.findAll().stream()
                .filter(movement -> item.id().equals(movement.getInventoryItemId()))
                .filter(movement -> "PurchaseOrder".equals(movement.getReferenceType()))
                .filter(reference -> referenceId.equals(reference.getReferenceId())))
                .hasSize(1);
    }

    @Test
    void databaseRejectsNegativeAggregateOrReservedAboveOnHand() {
        var warehouse = inventoryService.createWarehouse(new CreateWarehouseRequest(
                UUID.randomUUID(),
                "WH-CONSTRAINT-" + UUID.randomUUID(),
                "Constraint warehouse"
        ));
        InventoryItemResponse item = inventoryService.createItem(new CreateItemRequest(
                warehouse.id(),
                "SKU-CONSTRAINT-" + UUID.randomUUID(),
                "Constraint item",
                "MATERIAL",
                "KG"
        ));

        InventoryItemEntity invalid = itemRepository.findById(item.id()).orElseThrow();
        invalid.setOnHandQuantity(BigDecimal.ONE);
        invalid.setReservedQuantity(new BigDecimal("2.000"));

        assertThatThrownBy(() -> itemRepository.saveAndFlush(invalid))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
