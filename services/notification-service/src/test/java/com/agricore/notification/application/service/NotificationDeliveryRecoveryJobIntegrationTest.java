package com.agricore.notification.application.service;

import com.agricore.notification.infrastructure.delivery.NotificationDeliveryRecoveryJob;
import com.agricore.notification.infrastructure.delivery.TestNotificationDeliveryAdapter;
import com.agricore.notification.infrastructure.persistence.NotificationJpaRepository;
import com.agricore.notification.infrastructure.persistence.OutboxJpaRepository;
import com.agricore.notification.infrastructure.persistence.ProcessedEventJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class NotificationDeliveryRecoveryJobIntegrationTest {

    @Autowired
    private NotificationPersistenceService persistenceService;
    @Autowired
    private NotificationApplicationService applicationService;
    @Autowired
    private TestNotificationDeliveryAdapter deliveryAdapter;
    @Autowired
    private NotificationJpaRepository notificationRepository;
    @Autowired
    private OutboxJpaRepository outboxRepository;
    @Autowired
    private ProcessedEventJpaRepository processedEventRepository;

    @BeforeEach
    void setUp() {
        outboxRepository.deleteAll();
        processedEventRepository.deleteAll();
        notificationRepository.deleteAll();
        deliveryAdapter.reset();
    }

    @Test
    void recoversRequestPersistedBeforeAWorkerCouldClaimIt() {
        var requested = persistenceService.createRequested(new NotificationDraft(
                "EMAIL", "manager@agricore.local", "Harvest completed", "Batch HB-1 ready",
                "corr-recovery", null, "DirectApi", "delivery-recovery"
        ));
        assertThat(requested.getStatus()).isEqualTo("REQUESTED");

        new NotificationDeliveryRecoveryJob(
                persistenceService, applicationService, Duration.ofSeconds(30), 50
        ).recover();

        var recovered = notificationRepository.findById(requested.getId()).orElseThrow();
        assertThat(recovered.getStatus()).isEqualTo("SENT");
        assertThat(recovered.getDeliveryAttempts()).isEqualTo(1);
        assertThat(deliveryAdapter.attempts()).isEqualTo(1);
        assertThat(outboxRepository.findAll()).extracting(event -> event.getEventType())
                .containsExactlyInAnyOrder("NotificationRequested.v2", "NotificationSent.v2");
    }
}
