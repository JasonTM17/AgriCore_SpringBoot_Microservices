package com.agricore.inventory.api.controller;

import com.agricore.inventory.api.request.InternalStockOutRequest;
import com.agricore.inventory.api.response.InventoryItemResponse;
import com.agricore.inventory.application.service.InventoryApplicationService;
import com.agricore.inventory.infrastructure.security.InventoryInternalServiceTokenValidator;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/api/v1/inventory")
public class InventoryInternalController {

    private final InventoryApplicationService inventoryService;
    private final InventoryInternalServiceTokenValidator serviceTokenValidator;

    public InventoryInternalController(
            InventoryApplicationService inventoryService,
            InventoryInternalServiceTokenValidator serviceTokenValidator
    ) {
        this.inventoryService = inventoryService;
        this.serviceTokenValidator = serviceTokenValidator;
    }

    @PostMapping("/stock-out")
    public InventoryItemResponse stockOut(
            @RequestHeader(value = "X-Internal-Service-Token", required = false) String serviceToken,
            @Valid @RequestBody InternalStockOutRequest request
    ) {
        if (!serviceTokenValidator.matches(serviceToken)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Internal service authentication required");
        }
        return inventoryService.stockOutForFarm(request.farmId(), request.toStockOutRequest());
    }
}
