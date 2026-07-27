package com.agricore.notification.application.port;

public record NotificationDeliveryResult(
        boolean delivered,
        String errorCode,
        String errorMessage,
        boolean retryable
) {

    public static NotificationDeliveryResult sent() {
        return new NotificationDeliveryResult(true, null, null, false);
    }

    public static NotificationDeliveryResult failed(
            String errorCode,
            String errorMessage,
            boolean retryable
    ) {
        return new NotificationDeliveryResult(false, errorCode, errorMessage, retryable);
    }
}
