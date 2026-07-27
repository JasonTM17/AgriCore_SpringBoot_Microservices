package com.agricore.notification.infrastructure.delivery;

import com.agricore.notification.application.port.NotificationDeliveryPort;
import com.agricore.notification.application.port.NotificationDeliveryRequest;
import com.agricore.notification.application.port.NotificationDeliveryResult;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
@Primary
@ConditionalOnProperty(
        name = "agricore.notification.delivery.provider",
        havingValue = "smtp",
        matchIfMissing = true
)
public class RoutingNotificationDeliveryAdapter implements NotificationDeliveryPort {

    private final SmtpNotificationDeliveryAdapter smtpAdapter;
    private final InAppNotificationDeliveryAdapter inAppAdapter;

    public RoutingNotificationDeliveryAdapter(
            SmtpNotificationDeliveryAdapter smtpAdapter,
            InAppNotificationDeliveryAdapter inAppAdapter
    ) {
        this.smtpAdapter = smtpAdapter;
        this.inAppAdapter = inAppAdapter;
    }

    @Override
    public NotificationDeliveryResult deliver(NotificationDeliveryRequest request) {
        return switch (request.channel().toUpperCase(Locale.ROOT)) {
            case "EMAIL" -> smtpAdapter.deliver(request);
            case "IN_APP" -> inAppAdapter.deliver(request);
            default -> NotificationDeliveryResult.failed(
                    "UNSUPPORTED_CHANNEL",
                    "No delivery adapter is configured for channel " + request.channel(),
                    false
            );
        };
    }
}
