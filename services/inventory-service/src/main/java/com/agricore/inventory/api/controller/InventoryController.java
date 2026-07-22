package com.agricore.inventory.api.controller;

import com.agricore.inventory.api.request.*;
import com.agricore.inventory.api.response.InventoryHarvestProjectionAcknowledgementResponse;
import com.agricore.inventory.api.response.InventoryItemResponse;
import com.agricore.inventory.api.response.ReservationResponse;
import com.agricore.inventory.api.response.WarehouseResponse;
import com.agricore.inventory.application.service.HarvestProjectionAcknowledgementQueryService;
import com.agricore.inventory.application.service.InventoryApplicationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/inventory")
public class InventoryController {

    private final InventoryApplicationService service;
    private final HarvestProjectionAcknowledgementQueryService acknowledgementQueryService;

    public InventoryController(
            InventoryApplicationService service,
            HarvestProjectionAcknowledgementQueryService acknowledgementQueryService
    ) {
        this.service = service;
        this.acknowledgementQueryService = acknowledgementQueryService;
    }

    @PostMapping("/warehouses")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','WAREHOUSE_MANAGER')")
    public ResponseEntity<WarehouseResponse> createWarehouse(@Valid @RequestBody CreateWarehouseRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createWarehouse(request));
    }

    @PostMapping("/items")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','WAREHOUSE_MANAGER')")
    public ResponseEntity<InventoryItemResponse> createItem(@Valid @RequestBody CreateItemRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createItem(request));
    }

    @PostMapping("/stock-in")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','WAREHOUSE_MANAGER')")
    public InventoryItemResponse stockIn(@Valid @RequestBody StockInRequest request) {
        return service.stockIn(request);
    }

    @PostMapping("/stock-out")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','WAREHOUSE_MANAGER')")
    public InventoryItemResponse stockOut(@Valid @RequestBody StockOutRequest request) {
        return service.stockOut(request);
    }

    @PostMapping("/reservations")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','WAREHOUSE_MANAGER','SALES_STAFF')")
    public ResponseEntity<ReservationResponse> reserve(@Valid @RequestBody ReserveStockRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.reserve(request));
    }

    @PostMapping("/reservations/{reservationId}/release")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','WAREHOUSE_MANAGER','SALES_STAFF')")
    public ReservationResponse release(@PathVariable UUID reservationId) {
        return service.release(reservationId);
    }

    @PostMapping("/reservations/{reservationId}/confirm")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','WAREHOUSE_MANAGER','SALES_STAFF')")
    public ReservationResponse confirm(@PathVariable UUID reservationId) {
        return service.confirm(reservationId);
    }

    @GetMapping("/items/{itemId}")
    @PreAuthorize("isAuthenticated()")
    public InventoryItemResponse getItem(@PathVariable UUID itemId) {
        return service.getItem(itemId);
    }

    /**
     * Sync adapter simulating Kafka consumer for HarvestCompleted (idempotent).
     * Production path will consume from Kafka using the same application method.
     */
    @PostMapping("/events/harvest-completed")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','WAREHOUSE_MANAGER')")
    public InventoryItemResponse harvestCompleted(@Valid @RequestBody HarvestCompletedCommand command) {
        return service.processHarvestCompleted(command);
    }

    @GetMapping("/events/harvest-completed/{eventId}/acknowledgement")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','FARM_MANAGER','AGRONOMIST','WAREHOUSE_MANAGER')")
    public InventoryHarvestProjectionAcknowledgementResponse getHarvestProjectionAcknowledgement(
            @PathVariable UUID eventId
    ) {
        return acknowledgementQueryService.getAcknowledgement(eventId);
    }
}
