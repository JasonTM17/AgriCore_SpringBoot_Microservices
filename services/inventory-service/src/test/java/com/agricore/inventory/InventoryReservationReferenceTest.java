package com.agricore.inventory;

import com.agricore.inventory.api.request.CreateWarehouseRequest;
import com.agricore.inventory.api.request.HarvestCompletedCommand;
import com.agricore.inventory.api.request.ReserveStockRequest;
import com.agricore.inventory.api.response.InventoryItemResponse;
import com.agricore.inventory.application.service.InventoryApplicationService;
import com.agricore.inventory.domain.exception.InventoryException;
import com.agricore.inventory.infrastructure.persistence.InventoryReservationJpaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class InventoryReservationReferenceTest {

    @Autowired
    private InventoryApplicationService inventoryService;

    @Autowired
    private InventoryReservationJpaRepository reservationRepository;

    @Test
    void repeatedBusinessReferenceReturnsOneReservationWithoutDoubleHold() {
        InventoryItemResponse item = stockedItem("IDEMPOTENT");
        String referenceId = UUID.randomUUID().toString();
        var request = new ReserveStockRequest(
                item.id(),
                new BigDecimal("12.500"),
                "SalesOrder",
                referenceId
        );

        var first = inventoryService.reserve(request);
        var replay = inventoryService.reserve(request);
        var authoritative = inventoryService.getReservationByReference("SalesOrder", referenceId);

        assertThat(replay.id()).isEqualTo(first.id());
        assertThat(authoritative).isEqualTo(first);
        assertThat(reservationRepository.findByReferenceTypeAndReferenceId("SalesOrder", referenceId))
                .map(reservation -> reservation.getId())
                .contains(first.id());
        assertThat(inventoryService.getItem(item.id()).reservedQuantity()).isEqualByComparingTo("12.500");
    }

    @Test
    void reusedBusinessReferenceWithDifferentPayloadIsRejected() {
        InventoryItemResponse item = stockedItem("CONFLICT");
        String referenceId = UUID.randomUUID().toString();
        inventoryService.reserve(new ReserveStockRequest(
                item.id(),
                new BigDecimal("10.000"),
                "SalesOrder",
                referenceId
        ));

        assertThatThrownBy(() -> inventoryService.reserve(new ReserveStockRequest(
                item.id(),
                new BigDecimal("11.000"),
                "SalesOrder",
                referenceId
        ))).isInstanceOfSatisfying(
                InventoryException.class,
                exception -> assertThat(exception.getCode()).isEqualTo("RESERVATION_REFERENCE_CONFLICT")
        );
        assertThat(inventoryService.getItem(item.id()).reservedQuantity()).isEqualByComparingTo("10.000");
    }

    @Test
    void harvestForAnotherFarmCannotProjectIntoWarehouse() {
        UUID warehouseFarmId = UUID.randomUUID();
        var warehouse = inventoryService.createWarehouse(new CreateWarehouseRequest(
                warehouseFarmId,
                "WH-HARVEST-SCOPE-" + System.nanoTime(),
                "Harvest Scope Warehouse"
        ));

        assertThatThrownBy(() -> inventoryService.processHarvestCompleted(new HarvestCompletedCommand(
                UUID.randomUUID().toString(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                warehouse.id(),
                "PRODUCT-HARVEST-SCOPE",
                new BigDecimal("100.000"),
                "GRADE_A"
        ))).isInstanceOfSatisfying(
                InventoryException.class,
                exception -> assertThat(exception.getCode()).isEqualTo("HARVEST_FARM_MISMATCH")
        );
    }

    @Test
    void farmScopedReservationCannotBeReadOrMutatedFromAnotherFarm() {
        UUID ownerFarmId = UUID.randomUUID();
        UUID otherFarmId = UUID.randomUUID();
        InventoryItemResponse item = stockedItem(ownerFarmId, "FARM-SCOPE");
        String referenceId = UUID.randomUUID().toString();
        var request = new ReserveStockRequest(
                item.id(),
                new BigDecimal("15.000"),
                "SalesOrder",
                referenceId
        );
        var reservation = inventoryService.reserveForFarm(ownerFarmId, request);

        assertThatThrownBy(() -> inventoryService.reserveForFarm(otherFarmId, request))
                .isInstanceOfSatisfying(
                        InventoryException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo("ITEM_NOT_FOUND")
                );
        assertThatThrownBy(() -> inventoryService.getReservationByReferenceForFarm(
                otherFarmId,
                "SalesOrder",
                referenceId
        )).isInstanceOfSatisfying(
                InventoryException.class,
                exception -> assertThat(exception.getCode()).isEqualTo("ITEM_NOT_FOUND")
        );
        assertThatThrownBy(() -> inventoryService.releaseForFarm(otherFarmId, reservation.id()))
                .isInstanceOfSatisfying(
                        InventoryException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo("ITEM_NOT_FOUND")
                );
        assertThatThrownBy(() -> inventoryService.confirmForFarm(otherFarmId, reservation.id()))
                .isInstanceOfSatisfying(
                        InventoryException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo("ITEM_NOT_FOUND")
                );

        assertThat(inventoryService.getItem(item.id()).reservedQuantity()).isEqualByComparingTo("15.000");
        assertThat(inventoryService.getReservationByReferenceForFarm(
                ownerFarmId,
                "SalesOrder",
                referenceId
        )).isEqualTo(reservation);
    }

    private InventoryItemResponse stockedItem(String suffix) {
        return stockedItem(UUID.randomUUID(), suffix);
    }

    private InventoryItemResponse stockedItem(UUID farmId, String suffix) {
        var warehouse = inventoryService.createWarehouse(new CreateWarehouseRequest(
                farmId,
                "WH-REF-" + suffix + '-' + System.nanoTime(),
                "Reservation Reference Warehouse"
        ));
        return inventoryService.processHarvestCompleted(new HarvestCompletedCommand(
                UUID.randomUUID().toString(),
                warehouse.farmId(),
                UUID.randomUUID(),
                warehouse.id(),
                "PRODUCT-" + suffix,
                new BigDecimal("100.000"),
                "GRADE_A"
        ));
    }
}
