package com.agricore.notification.application.port;

public interface NotificationDeliveryPort {

    NotificationDeliveryResult deliver(NotificationDeliveryRequest request);
}
