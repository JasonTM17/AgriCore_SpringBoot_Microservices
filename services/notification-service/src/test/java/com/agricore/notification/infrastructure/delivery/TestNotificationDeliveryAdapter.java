package com.agricore.notification.infrastructure.delivery;

import com.agricore.notification.application.port.NotificationDeliveryPort;
import com.agricore.notification.application.port.NotificationDeliveryRequest;
import com.agricore.notification.application.port.NotificationDeliveryResult;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("test")
@ConditionalOnProperty(name = "agricore.notification.delivery.provider", havingValue = "stub")
public class TestNotificationDeliveryAdapter implements NotificationDeliveryPort {

    @Override
    public NotificationDeliveryResult deliver(NotificationDeliveryRequest request) {
        return NotificationDeliveryResult.sent();
    }
}
