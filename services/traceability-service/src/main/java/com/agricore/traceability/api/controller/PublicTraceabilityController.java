package com.agricore.traceability.api.controller;

import com.agricore.traceability.api.request.CreateTraceabilityRequest;
import com.agricore.traceability.api.response.PublicTraceabilityResponse;
import com.agricore.traceability.application.service.TraceabilityApplicationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
public class PublicTraceabilityController {

    private final TraceabilityApplicationService service;

    public PublicTraceabilityController(TraceabilityApplicationService service) {
        this.service = service;
    }

    @GetMapping("/public/api/v1/traceability/{traceabilityCode}")
    public PublicTraceabilityResponse getPublic(@PathVariable String traceabilityCode) {
        return service.getPublic(traceabilityCode);
    }

    /**
     * Internal projection writer (event adapter / staff backfill).
     * Not public — requires warehouse or admin role so FIELD_WORKER cannot invent QR rows.
     */
    @PostMapping("/api/v1/traceability/batches")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','WAREHOUSE_MANAGER')")
    public ResponseEntity<PublicTraceabilityResponse> create(@Valid @RequestBody CreateTraceabilityRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createFromHarvest(request));
    }
}
