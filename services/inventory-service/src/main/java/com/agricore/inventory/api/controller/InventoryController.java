package com.agricore.inventory.api.controller;

import com.agricore.common.api.PageResponse;
import com.agricore.inventory.api.request.*;
import com.agricore.inventory.api.response.InventoryHarvestProjectionAcknowledgementResponse;
import com.agricore.inventory.api.response.InventoryItemResponse;
import com.agricore.inventory.api.response.ReservationResponse;
import com.agricore.inventory.api.response.StockMovementResponse;
import com.agricore.inventory.api.response.WarehouseResponse;
import com.agricore.inventory.application.service.HarvestProjectionAcknowledgementQueryService;
import com.agricore.inventory.application.service.InventoryAccessGuard;
import com.agricore.inventory.application.service.InventoryApplicationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/inventory")
@Validated
public class InventoryController {

    private static final String ITEM_SORT_PATTERN =
            "(sku|name|createdAt|updatedAt),(?i:asc|desc)";
    private static final String MOVEMENT_SORT_PATTERN =
            "(createdAt|quantity|movementType),(?i:asc|desc)";

    private final InventoryApplicationService service;
    private final InventoryAccessGuard accessGuard;
    private final HarvestProjectionAcknowledgementQueryService acknowledgementQueryService;

    public InventoryController(
            InventoryApplicationService service,
            InventoryAccessGuard accessGuard,
            HarvestProjectionAcknowledgementQueryService acknowledgementQueryService
    ) {
        this.service = service;
        this.accessGuard = accessGuard;
        this.acknowledgementQueryService = acknowledgementQueryService;
    }

    @PostMapping("/warehouses")
    @PreAuthorize("hasAuthority('PERMISSION_INVENTORY_WRITE')")
    public ResponseEntity<WarehouseResponse> createWarehouse(@Valid @RequestBody CreateWarehouseRequest request) {
        accessGuard.requireFarm(request.farmId());
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createWarehouse(request));
    }

    @PostMapping("/items")
    @PreAuthorize("hasAuthority('PERMISSION_INVENTORY_WRITE')")
    public ResponseEntity<InventoryItemResponse> createItem(@Valid @RequestBody CreateItemRequest request) {
        accessGuard.requireWarehouse(request.warehouseId());
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createItem(request));
    }

    @PostMapping("/stock-in")
    @PreAuthorize("hasAuthority('PERMISSION_INVENTORY_WRITE')")
    public InventoryItemResponse stockIn(@Valid @RequestBody StockInRequest request) {
        accessGuard.requireItem(request.inventoryItemId());
        return service.stockIn(request);
    }

    @PostMapping("/stock-out")
    @PreAuthorize("hasAuthority('PERMISSION_INVENTORY_WRITE')")
    public InventoryItemResponse stockOut(@Valid @RequestBody StockOutRequest request) {
        accessGuard.requireItem(request.inventoryItemId());
        return service.stockOut(request);
    }

    @PostMapping("/reservations")
    @PreAuthorize("hasAuthority('PERMISSION_INVENTORY_USE')")
    public ResponseEntity<ReservationResponse> reserve(@Valid @RequestBody ReserveStockRequest request) {
        accessGuard.requireItem(request.inventoryItemId());
        return ResponseEntity.status(HttpStatus.CREATED).body(service.reserve(request));
    }

    @GetMapping("/reservations/by-reference")
    @PreAuthorize("hasAuthority('PERMISSION_INVENTORY_USE')")
    public ReservationResponse getReservationByReference(
            @RequestParam @NotBlank @Size(max = 64) String referenceType,
            @RequestParam @NotBlank @Size(max = 100) String referenceId
    ) {
        accessGuard.requireReservationReference(referenceType, referenceId);
        return service.getReservationByReference(referenceType, referenceId);
    }

    @PostMapping("/reservations/{reservationId}/release")
    @PreAuthorize("hasAuthority('PERMISSION_INVENTORY_USE')")
    public ReservationResponse release(@PathVariable UUID reservationId) {
        accessGuard.requireReservation(reservationId);
        return service.release(reservationId);
    }

    @PostMapping("/reservations/{reservationId}/confirm")
    @PreAuthorize("hasAuthority('PERMISSION_INVENTORY_USE')")
    public ReservationResponse confirm(@PathVariable UUID reservationId) {
        accessGuard.requireReservation(reservationId);
        return service.confirm(reservationId);
    }

    @GetMapping("/items/{itemId}")
    @PreAuthorize("hasAuthority('PERMISSION_INVENTORY_READ')")
    public InventoryItemResponse getItem(@PathVariable UUID itemId) {
        accessGuard.requireItem(itemId);
        return service.getItem(itemId);
    }

    @GetMapping("/warehouses/{warehouseId}/items")
    @PreAuthorize("hasAuthority('PERMISSION_INVENTORY_READ')")
    public PageResponse<InventoryItemResponse> listItems(
            @PathVariable UUID warehouseId,
            @RequestParam(defaultValue = "0") @Min(0) @Max(10000) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "sku,asc") @Pattern(regexp = ITEM_SORT_PATTERN) String sort
    ) {
        accessGuard.requireWarehouse(warehouseId);
        return service.listItems(
                warehouseId,
                PageRequest.of(page, size, parseSort(sort))
        );
    }

    @GetMapping("/items/{itemId}/movements")
    @PreAuthorize("hasAuthority('PERMISSION_INVENTORY_READ')")
    public PageResponse<StockMovementResponse> listMovements(
            @PathVariable UUID itemId,
            @RequestParam(defaultValue = "0") @Min(0) @Max(10000) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "createdAt,desc") @Pattern(regexp = MOVEMENT_SORT_PATTERN) String sort
    ) {
        accessGuard.requireItem(itemId);
        return service.listMovements(
                itemId,
                PageRequest.of(page, size, parseSort(sort))
        );
    }

    /**
     * Sync adapter simulating Kafka consumer for HarvestCompleted (idempotent).
     * Production path will consume from Kafka using the same application method.
     */
    @PostMapping("/events/harvest-completed")
    @PreAuthorize("hasAuthority('PERMISSION_INVENTORY_WRITE')")
    public InventoryItemResponse harvestCompleted(@Valid @RequestBody HarvestCompletedCommand command) {
        accessGuard.requireWarehouse(command.warehouseId());
        return service.processHarvestCompleted(command);
    }

    @GetMapping("/events/harvest-completed/{eventId}/acknowledgement")
    @PreAuthorize("hasAuthority('PERMISSION_INVENTORY_READ')")
    public InventoryHarvestProjectionAcknowledgementResponse getHarvestProjectionAcknowledgement(
            @PathVariable UUID eventId,
            @RequestParam UUID warehouseId
    ) {
        UUID farmId = accessGuard.requireWarehouse(warehouseId);
        return acknowledgementQueryService.getAcknowledgement(eventId, warehouseId, farmId);
    }

    private static Sort parseSort(String sort) {
        String[] parts = sort.split(",");
        if (parts.length == 2 && "desc".equalsIgnoreCase(parts[1])) {
            return Sort.by(parts[0]).descending();
        }
        return Sort.by(parts[0]).ascending();
    }
}
