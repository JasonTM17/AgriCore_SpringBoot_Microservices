package com.agricore.inventory.application.service;

import com.agricore.farmaccess.FarmAccessClient;
import com.agricore.inventory.domain.exception.InventoryException;
import com.agricore.inventory.infrastructure.persistence.InventoryItemJpaRepository;
import com.agricore.inventory.infrastructure.persistence.InventoryReservationJpaRepository;
import com.agricore.inventory.infrastructure.persistence.WarehouseJpaRepository;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Enforces the authoritative farm boundary before public inventory operations.
 * Broker consumers and token-authenticated internal adapters use separate entry points.
 */
@Component
public class InventoryAccessGuard {

    private final FarmAccessClient farmAccessClient;
    private final WarehouseJpaRepository warehouseRepository;
    private final InventoryItemJpaRepository itemRepository;
    private final InventoryReservationJpaRepository reservationRepository;

    public InventoryAccessGuard(
            FarmAccessClient farmAccessClient,
            WarehouseJpaRepository warehouseRepository,
            InventoryItemJpaRepository itemRepository,
            InventoryReservationJpaRepository reservationRepository
    ) {
        this.farmAccessClient = farmAccessClient;
        this.warehouseRepository = warehouseRepository;
        this.itemRepository = itemRepository;
        this.reservationRepository = reservationRepository;
    }

    public void requireFarm(UUID farmId) {
        farmAccessClient.requireFarm(farmId);
    }

    public UUID requireWarehouse(UUID warehouseId) {
        var warehouse = warehouseRepository.findById(warehouseId)
                .orElseThrow(() -> new InventoryException(
                        "WAREHOUSE_NOT_FOUND",
                        "Warehouse not found",
                        404
                ));
        if (warehouse.getFarmId() == null) {
            throw new InventoryException(
                    "WAREHOUSE_SCOPE_UNAVAILABLE",
                    "Warehouse farm scope is unavailable",
                    503
            );
        }
        requireFarm(warehouse.getFarmId());
        return warehouse.getFarmId();
    }

    public void requireItem(UUID itemId) {
        var item = itemRepository.findById(itemId)
                .orElseThrow(() -> new InventoryException(
                        "ITEM_NOT_FOUND",
                        "Inventory item not found",
                        404
                ));
        requireWarehouse(item.getWarehouseId());
    }

    public void requireReservation(UUID reservationId) {
        var reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new InventoryException(
                        "RESERVATION_NOT_FOUND",
                        "Reservation not found",
                        404
                ));
        requireItem(reservation.getInventoryItemId());
    }

    public void requireReservationReference(String referenceType, String referenceId) {
        var reservation = reservationRepository
                .findByReferenceTypeAndReferenceId(referenceType.trim(), referenceId.trim())
                .orElseThrow(() -> new InventoryException(
                        "RESERVATION_NOT_FOUND",
                        "Reservation not found",
                        404
                ));
        requireItem(reservation.getInventoryItemId());
    }
}
