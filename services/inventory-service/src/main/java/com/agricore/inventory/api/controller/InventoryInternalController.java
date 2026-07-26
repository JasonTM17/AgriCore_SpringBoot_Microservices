package com.agricore.inventory.api.controller;

import com.agricore.inventory.api.request.InternalStockOutRequest;
import com.agricore.inventory.api.request.InternalReserveStockRequest;
import com.agricore.inventory.api.response.InventoryItemResponse;
import com.agricore.inventory.api.response.ReservationResponse;
import com.agricore.inventory.application.service.InventoryApplicationService;
import com.agricore.inventory.infrastructure.security.InventoryInternalServiceTokenValidator;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/internal/api/v1/inventory")
@Validated
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
        requireServiceToken(serviceToken);
        return inventoryService.stockOutForFarm(request.farmId(), request.toStockOutRequest());
    }

    @PostMapping("/reservations")
    public ReservationResponse reserve(
            @RequestHeader(value = "X-Internal-Service-Token", required = false) String serviceToken,
            @Valid @RequestBody InternalReserveStockRequest request
    ) {
        requireServiceToken(serviceToken);
        return inventoryService.reserveForFarm(request.farmId(), request.toReserveStockRequest());
    }

    @GetMapping("/reservations/by-reference")
    public ReservationResponse getReservationByReference(
            @RequestHeader(value = "X-Internal-Service-Token", required = false) String serviceToken,
            @RequestParam UUID farmId,
            @RequestParam @NotBlank @Size(max = 64) String referenceType,
            @RequestParam @NotBlank @Size(max = 100) String referenceId
    ) {
        requireServiceToken(serviceToken);
        return inventoryService.getReservationByReferenceForFarm(farmId, referenceType, referenceId);
    }

    @PostMapping("/reservations/{reservationId}/release")
    public ReservationResponse release(
            @RequestHeader(value = "X-Internal-Service-Token", required = false) String serviceToken,
            @RequestParam UUID farmId,
            @PathVariable UUID reservationId
    ) {
        requireServiceToken(serviceToken);
        return inventoryService.releaseForFarm(farmId, reservationId);
    }

    @PostMapping("/reservations/{reservationId}/confirm")
    public ReservationResponse confirm(
            @RequestHeader(value = "X-Internal-Service-Token", required = false) String serviceToken,
            @RequestParam UUID farmId,
            @PathVariable UUID reservationId
    ) {
        requireServiceToken(serviceToken);
        return inventoryService.confirmForFarm(farmId, reservationId);
    }

    private void requireServiceToken(String serviceToken) {
        if (!serviceTokenValidator.matches(serviceToken)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Internal service authentication required");
        }
    }
}
