package com.agricore.sales.api.controller;

import com.agricore.sales.api.request.CreateCustomerRequest;
import com.agricore.sales.api.request.CreateOrderRequest;
import com.agricore.sales.api.response.SalesOrderResponse;
import com.agricore.sales.application.service.SalesSagaService;
import com.agricore.sales.infrastructure.persistence.entity.CustomerEntity;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/sales")
public class SalesController {

    private final SalesSagaService salesService;

    public SalesController(SalesSagaService salesService) {
        this.salesService = salesService;
    }

    @PostMapping("/customers")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','SALES_STAFF')")
    public ResponseEntity<Map<String, Object>> createCustomer(@Valid @RequestBody CreateCustomerRequest request) {
        CustomerEntity c = salesService.createCustomer(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "id", c.getId(),
                "code", c.getCode(),
                "name", c.getName()
        ));
    }

    @PostMapping("/orders")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','SALES_STAFF')")
    public ResponseEntity<SalesOrderResponse> placeOrder(@Valid @RequestBody CreateOrderRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(salesService.placeOrder(request));
    }

    @GetMapping("/orders/{orderId}")
    @PreAuthorize("isAuthenticated()")
    public SalesOrderResponse get(@PathVariable UUID orderId) {
        return salesService.get(orderId);
    }
}
