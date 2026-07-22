package com.agricore.notification.infrastructure.delivery;

import com.agricore.notification.application.port.NotificationDeliveryPort;
import com.agricore.notification.application.port.NotificationDeliveryRequest;
import com.agricore.notification.application.port.NotificationDeliveryResult;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@Profile("test")
@ConditionalOnProperty(name = "agricore.notification.delivery.provider", havingValue = "stub")
public class TestNotificationDeliveryAdapter implements NotificationDeliveryPort {

    private final Deque<NotificationDeliveryResult> results = new ArrayDeque<>();
    private final AtomicInteger attempts = new AtomicInteger();

    @Override
    public synchronized NotificationDeliveryResult deliver(NotificationDeliveryRequest request) {
        attempts.incrementAndGet();
        return results.isEmpty() ? NotificationDeliveryResult.sent() : results.removeFirst();
    }

    public synchronized void respondWith(NotificationDeliveryResult... configuredResults) {
        results.clear();
        for (NotificationDeliveryResult result : configuredResults) {
            results.addLast(result);
        }
        attempts.set(0);
    }

    public synchronized void reset() {
        results.clear();
        attempts.set(0);
    }

    public int attempts() {
        return attempts.get();
    }
}
