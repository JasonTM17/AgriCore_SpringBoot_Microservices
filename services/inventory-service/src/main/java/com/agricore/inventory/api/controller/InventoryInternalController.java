package com.agricore.inventory.api.controller;

import com.agricore.inventory.api.request.StockOutRequest;
import com.agricore.inventory.api.response.InventoryItemResponse;
import com.agricore.inventory.application.service.InventoryApplicationService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/api/v1/inventory")
public class InventoryInternalController {

    private final InventoryApplicationService inventoryService;

    public InventoryInternalController(InventoryApplicationService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @PostMapping("/stock-out")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','FARM_MANAGER','AGRONOMIST','FIELD_WORKER')")
    public InventoryItemResponse stockOut(@Valid @RequestBody StockOutRequest request) {
        return inventoryService.stockOut(request);
    }
}
