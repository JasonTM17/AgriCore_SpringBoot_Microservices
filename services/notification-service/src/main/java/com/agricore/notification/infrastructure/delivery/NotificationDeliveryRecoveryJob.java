package com.agricore.notification.infrastructure.delivery;

import com.agricore.notification.application.service.NotificationApplicationService;
import com.agricore.notification.application.service.NotificationPersistenceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Component
@ConditionalOnProperty(
        name = "agricore.notification.delivery.recovery.enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class NotificationDeliveryRecoveryJob {

    private static final Logger log = LoggerFactory.getLogger(NotificationDeliveryRecoveryJob.class);

    private final NotificationPersistenceService persistenceService;
    private final NotificationApplicationService applicationService;
    private final Duration deliveryLease;
    private final int batchSize;

    public NotificationDeliveryRecoveryJob(
            NotificationPersistenceService persistenceService,
            NotificationApplicationService applicationService,
            @Value("${agricore.notification.delivery.lease:PT30S}") Duration deliveryLease,
            @Value("${agricore.notification.delivery.recovery.batch-size:50}") int batchSize
    ) {
        this.persistenceService = persistenceService;
        this.applicationService = applicationService;
        this.deliveryLease = deliveryLease;
        this.batchSize = Math.max(1, Math.min(batchSize, 500));
    }

    @Scheduled(fixedDelayString = "${agricore.notification.delivery.recovery.poll-ms:30000}")
    public void recover() {
        Instant staleBefore = Instant.now().minus(deliveryLease);
        for (UUID notificationId : persistenceService.findRecoverableIds(staleBefore, batchSize)) {
            try {
                applicationService.retryExisting(notificationId);
            } catch (RuntimeException exception) {
                log.warn("notification_recovery_failed notificationId={} errorType={}",
                        notificationId, exception.getClass().getSimpleName());
            }
        }
    }
}
