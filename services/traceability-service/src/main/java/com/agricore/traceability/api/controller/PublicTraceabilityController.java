package com.agricore.traceability.api.controller;

import com.agricore.traceability.api.response.PublicTraceabilityResponse;
import com.agricore.traceability.application.service.TraceabilityApplicationService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
}
