package com.agricore.traceability.api.controller;

import com.agricore.traceability.api.response.TraceabilityHarvestProjectionAcknowledgementResponse;
import com.agricore.traceability.application.service.TraceabilityProjectionAcknowledgementQueryService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
public class TraceabilityProjectionAcknowledgementController {

    private final TraceabilityProjectionAcknowledgementQueryService queryService;

    public TraceabilityProjectionAcknowledgementController(
            TraceabilityProjectionAcknowledgementQueryService queryService
    ) {
        this.queryService = queryService;
    }

    @GetMapping("/api/v1/traceability/events/harvest-completed/{eventId}/acknowledgement")
    @PreAuthorize("hasAuthority('PERMISSION_TRACEABILITY_USE')")
    public TraceabilityHarvestProjectionAcknowledgementResponse getAcknowledgement(
            @PathVariable UUID eventId
    ) {
        return queryService.getAcknowledgement(eventId);
    }
}
