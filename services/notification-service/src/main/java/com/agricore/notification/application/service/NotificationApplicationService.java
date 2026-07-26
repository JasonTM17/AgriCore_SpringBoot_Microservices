package com.agricore.notification.application.service;

import com.agricore.notification.api.request.SendNotificationRequest;
import com.agricore.notification.api.request.UserRegisteredCommand;
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

    /** Identifies this consumer in processed_events so other consumers stay independent. */
    static final String USER_REGISTERED_CONSUMER = "notification-user-registered";

    private final NotificationJpaRepository repository;
    private final ProcessedEventJpaRepository processedEventRepository;

    public NotificationApplicationService(
            NotificationJpaRepository repository,
            ProcessedEventJpaRepository processedEventRepository
    ) {
        this.repository = repository;
        this.processedEventRepository = processedEventRepository;
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

    /**
     * Records the welcome notification for a newly registered user.
     * The processed-event marker and the notification commit in one transaction,
     * so a redelivered eventId can never produce a second notification.
     */
    @Transactional
    public void recordUserRegistered(UserRegisteredCommand command) {
        if (processedEventRepository.existsByEventIdAndConsumerName(command.eventId(), USER_REGISTERED_CONSUMER)) {
            log.debug("Skipping already-processed UserRegistered eventId={}", command.eventId());
            return;
        }

        Instant now = Instant.now();
        NotificationEntity n = new NotificationEntity();
        n.setId(UUID.randomUUID());
        n.setChannel("EMAIL");
        n.setRecipient(command.email());
        n.setSubject("Welcome to AgriCore");
        n.setBody(welcomeBody(command));
        n.setCorrelationId(command.eventId());
        n.setStatus("SENT");
        n.setCreatedAt(now);
        n.setSentAt(now);
        repository.save(n);

        processedEventRepository.save(
                ProcessedEventEntity.of(command.eventId(), USER_REGISTERED_CONSUMER)
        );

        log.info("notification_sent channel=EMAIL recipient={} subject=welcome userId={} eventId={}",
                n.getRecipient(), command.userId(), command.eventId());
    }

    private static String welcomeBody(UserRegisteredCommand command) {
        String roles = command.roles() == null || command.roles().isEmpty()
                ? "none"
                : String.join(", ", command.roles());
        return "Hello %s, your AgriCore account is ready. Assigned roles: %s."
                .formatted(command.fullName(), roles);
    }
}
