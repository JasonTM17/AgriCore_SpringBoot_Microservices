package com.agricore.assistant.infrastructure.persistence;

import com.agricore.assistant.infrastructure.persistence.entity.GenerationEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface GenerationEventJpaRepository extends JpaRepository<GenerationEventEntity, UUID> {
    List<GenerationEventEntity> findByGenerationIdAndSequenceNoGreaterThanOrderBySequenceNoAsc(
            UUID generationId,
            long afterSequence
    );

    @Query("select coalesce(max(e.sequenceNo), -1) from GenerationEventEntity e where e.generationId = :generationId")
    long maxSequence(@Param("generationId") UUID generationId);
}
