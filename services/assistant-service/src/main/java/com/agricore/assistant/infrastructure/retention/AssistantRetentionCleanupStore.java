package com.agricore.assistant.infrastructure.retention;

import com.agricore.assistant.infrastructure.persistence.repository.AssistantAuditEventJpaRepository;
import com.agricore.assistant.infrastructure.persistence.repository.ConversationJpaRepository;
import com.agricore.assistant.infrastructure.persistence.repository.GenerationEventJpaRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Component
public class AssistantRetentionCleanupStore {

    private final GenerationEventJpaRepository generationEventRepository;
    private final ConversationJpaRepository conversationRepository;
    private final AssistantAuditEventJpaRepository auditEventRepository;

    public AssistantRetentionCleanupStore(
            GenerationEventJpaRepository generationEventRepository,
            ConversationJpaRepository conversationRepository,
            AssistantAuditEventJpaRepository auditEventRepository
    ) {
        this.generationEventRepository = generationEventRepository;
        this.conversationRepository = conversationRepository;
        this.auditEventRepository = auditEventRepository;
    }

    @Transactional
    public CleanupResult purgeExpired(Instant now, int batchSize) {
        int generationEvents = generationEventRepository.deleteExpiredBatch(now, batchSize);
        int conversations = conversationRepository.deleteExpiredBatch(now, batchSize);
        int auditEvents = auditEventRepository.deleteExpiredBatch(now, batchSize);
        return new CleanupResult(generationEvents, conversations, auditEvents);
    }

    public record CleanupResult(
            int generationEvents,
            int conversations,
            int auditEvents
    ) {
        public int total() {
            return generationEvents + conversations + auditEvents;
        }
    }
}
