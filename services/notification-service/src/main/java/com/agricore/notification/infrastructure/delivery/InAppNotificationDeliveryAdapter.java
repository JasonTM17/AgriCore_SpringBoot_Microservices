package com.agricore.notification.infrastructure.delivery;

import com.agricore.notification.application.port.NotificationDeliveryRequest;
import com.agricore.notification.application.port.NotificationDeliveryResult;
import com.agricore.notification.infrastructure.persistence.InAppDeliveryJpaRepository;
import com.agricore.notification.infrastructure.persistence.entity.InAppDeliveryEntity;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Component
public class InAppNotificationDeliveryAdapter {

    private final InAppDeliveryJpaRepository repository;

    public InAppNotificationDeliveryAdapter(InAppDeliveryJpaRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public NotificationDeliveryResult deliver(NotificationDeliveryRequest request) {
        if (!"IN_APP".equalsIgnoreCase(request.channel())) {
            return NotificationDeliveryResult.failed(
                    "UNSUPPORTED_CHANNEL",
                    "In-app adapter supports IN_APP only",
                    false
            );
        }
        if (repository.existsById(request.notificationId())) {
            return NotificationDeliveryResult.sent();
        }

        InAppDeliveryEntity delivery = new InAppDeliveryEntity();
        delivery.setNotificationId(request.notificationId());
        delivery.setRecipient(request.recipient());
        delivery.setSubject(request.subject());
        delivery.setBody(request.body());
        delivery.setDeliveredAt(Instant.now());
        repository.saveAndFlush(delivery);
        return NotificationDeliveryResult.sent();
    }
}
