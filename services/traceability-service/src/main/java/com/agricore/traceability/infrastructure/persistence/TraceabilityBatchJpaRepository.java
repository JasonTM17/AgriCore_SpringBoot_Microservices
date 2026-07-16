package com.agricore.traceability.infrastructure.persistence;

import com.agricore.traceability.infrastructure.persistence.entity.TraceabilityBatchEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface TraceabilityBatchJpaRepository extends JpaRepository<TraceabilityBatchEntity, UUID> {
    Optional<TraceabilityBatchEntity> findByTraceabilityCode(String code);
}
