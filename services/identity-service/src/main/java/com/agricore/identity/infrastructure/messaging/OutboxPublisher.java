package com.agricore.identity.infrastructure.messaging;

import com.agricore.identity.infrastructure.persistence.OutboxJpaRepository;
import com.agricore.identity.infrastructure.persistence.entity.OutboxEventEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Polling transactional outbox publisher for identity domain events.
 * Disabled in tests via agricore.outbox.publisher.enabled=false.
 */
@Component
@ConditionalOnProperty(name = "agricore.outbox.publisher.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final OutboxJpaRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public OutboxPublisher(OutboxJpaRepository outboxRepository, KafkaTemplate<String, String> kafkaTemplate) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Scheduled(fixedDelayString = "${agricore.outbox.publisher.poll-ms:1000}")
    @Transactional
    public void publishPending() {
        List<OutboxEventEntity> batch = outboxRepository.findUnpublished(PageRequest.of(0, 50));
        for (OutboxEventEntity event : batch) {
            try {
                kafkaTemplate.send(event.getTopic(), event.getId().toString(), event.getPayload()).get();
                event.markPublished();
                outboxRepository.save(event);
                log.debug("Published identity outbox event {} type={}", event.getId(), event.getEventType());
            } catch (Exception ex) {
                event.markFailed(ex.getMessage());
                outboxRepository.save(event);
                log.warn("Failed to publish identity outbox event {}: {}", event.getId(), ex.getMessage());
            }
        }
    }
}
