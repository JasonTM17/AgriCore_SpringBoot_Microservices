package com.agricore.notification.application.service;

import com.agricore.notification.api.request.SendNotificationRequest;
import com.agricore.notification.api.response.NotificationResponse;
import com.agricore.notification.infrastructure.persistence.NotificationJpaRepository;
import com.agricore.notification.infrastructure.persistence.ProcessedEventJpaRepository;
import com.agricore.notification.infrastructure.persistence.entity.NotificationEntity;
import com.agricore.notification.infrastructure.persistence.entity.ProcessedEventEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Records and "sends" notifications (dev: log sink; production: email/webhook adapter).
 */
@Service
public class NotificationApplicationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationApplicationService.class);

    private final NotificationJpaRepository repository;
    private final ProcessedEventJpaRepository processedEventRepository;
    private final NotificationEventOutboxWriter eventOutboxWriter;

    public NotificationApplicationService(
            NotificationJpaRepository repository,
            ProcessedEventJpaRepository processedEventRepository,
            NotificationEventOutboxWriter eventOutboxWriter
    ) {
        this.repository = repository;
        this.processedEventRepository = processedEventRepository;
        this.eventOutboxWriter = eventOutboxWriter;
    }

    @Transactional
    public NotificationResponse send(SendNotificationRequest request) {
        Instant now = Instant.now();
        NotificationEntity n = new NotificationEntity();
        n.setId(UUID.randomUUID());
        n.setChannel(request.channel().trim().toUpperCase());
        n.setRecipient(request.recipient().trim());
        n.setSubject(request.subject().trim());
        n.setBody(request.body());
        n.setCorrelationId(request.correlationId());
        n.setStatus("SENT");
        n.setCreatedAt(now);
        n.setSentAt(now);
        repository.save(n);
        log.info("notification_sent channel={} recipient={} subject={} correlationId={}",
                n.getChannel(), n.getRecipient(), n.getSubject(), n.getCorrelationId());
        return new NotificationResponse(
                n.getId(), n.getChannel(), n.getRecipient(), n.getSubject(),
                n.getStatus(), n.getCorrelationId(), n.getCreatedAt(), n.getSentAt()
        );
    }

    @Transactional
    public boolean consume(NotificationEventCommand command) {
        final String consumerName = "notification-service";
        if (processedEventRepository.existsByEventIdAndConsumerName(command.eventId(), consumerName)) {
            log.debug("Skipping duplicate notification event {} type={}", command.eventId(), command.eventType());
            return false;
        }

        Instant now = Instant.now();
        NotificationEntity notification = new NotificationEntity();
        notification.setId(UUID.randomUUID());
        notification.setChannel(command.channel().trim().toUpperCase());
        notification.setRecipient(command.recipient().trim());
        notification.setSubject(command.subject().trim());
        notification.setBody(command.body());
        notification.setCorrelationId(command.correlationId());
        notification.setSourceEventId(command.eventId());
        notification.setStatus("SENT");
        notification.setCreatedAt(now);
        notification.setSentAt(now);
        repository.save(notification);
        eventOutboxWriter.notificationRequested(notification, command.eventType());
        eventOutboxWriter.notificationSent(notification, command.eventType());
        processedEventRepository.save(ProcessedEventEntity.create(command.eventId(), consumerName));
        log.info("notification_event_processed eventId={} eventType={} channel={} recipient={}",
                command.eventId(), command.eventType(), notification.getChannel(), notification.getRecipient());
        return true;
    }
}
