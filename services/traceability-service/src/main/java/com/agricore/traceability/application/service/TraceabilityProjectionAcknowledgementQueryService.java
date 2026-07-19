package com.agricore.traceability.application.service;

import com.agricore.traceability.api.response.TraceabilityHarvestProjectionAcknowledgementResponse;
import com.agricore.traceability.infrastructure.persistence.ProcessedEventJpaRepository;
import com.agricore.traceability.infrastructure.persistence.entity.ProcessedEventEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
public class TraceabilityProjectionAcknowledgementQueryService {

    private final ProcessedEventJpaRepository processedEventRepository;

    public TraceabilityProjectionAcknowledgementQueryService(
            ProcessedEventJpaRepository processedEventRepository
    ) {
        this.processedEventRepository = processedEventRepository;
    }

    @Transactional(readOnly = true)
    public TraceabilityHarvestProjectionAcknowledgementResponse getAcknowledgement(UUID eventId) {
        return findProcessedEvent(eventId)
                .map(processedEvent -> new TraceabilityHarvestProjectionAcknowledgementResponse(
                        eventId,
                        "TRACEABILITY",
                        TraceabilityHarvestProjectionAcknowledgementResponse.State.ACKNOWLEDGED,
                        processedEvent.getProcessedAt()
                ))
                .orElseGet(() -> new TraceabilityHarvestProjectionAcknowledgementResponse(
                        eventId,
                        "TRACEABILITY",
                        TraceabilityHarvestProjectionAcknowledgementResponse.State.NOT_ACKNOWLEDGED,
                        null
                ));
    }

    private Optional<ProcessedEventEntity> findProcessedEvent(UUID eventId) {
        return processedEventRepository.findCanonicalOrLegacy(
                eventId.toString(),
                TraceabilityApplicationService.HARVEST_CONSUMER
        );
    }
}
