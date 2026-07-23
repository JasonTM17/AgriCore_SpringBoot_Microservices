package com.agricore.traceability.api.controller;

import com.agricore.traceability.api.request.CreateTraceabilityRequest;
import com.agricore.traceability.api.response.PublicTraceabilityResponse;
import com.agricore.traceability.application.service.TraceabilityApplicationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
    public PublicTraceabilityResponse getPublic(
            @PathVariable @NotBlank @Size(max = 64) String traceabilityCode
    ) {
        return service.getPublic(traceabilityCode);
    }

    @GetMapping(
            value = "/public/api/v1/traceability/{traceabilityCode}/qr",
            produces = MediaType.IMAGE_PNG_VALUE
    )
    public ResponseEntity<byte[]> getPublicQrCode(
            @PathVariable @NotBlank @Size(max = 64) String traceabilityCode
    ) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=86400, immutable")
                .contentType(MediaType.IMAGE_PNG)
                .body(service.getPublicQrCode(traceabilityCode));
    }

    /**
     * Internal projection writer (event adapter / staff backfill).
     * Not public — requires warehouse or admin role so FIELD_WORKER cannot invent QR rows.
     */
    @PostMapping("/api/v1/traceability/batches")
    @PreAuthorize("hasAuthority('PERMISSION_TRACEABILITY_WRITE')")
    public ResponseEntity<PublicTraceabilityResponse> create(@Valid @RequestBody CreateTraceabilityRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createFromHarvest(request));
    }
}
