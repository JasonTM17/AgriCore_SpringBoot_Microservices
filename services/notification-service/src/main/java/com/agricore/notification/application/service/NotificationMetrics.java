package com.agricore.notification.application.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class NotificationMetrics {

    private final Counter delivered;
    private final Counter failed;
    private final Counter duplicates;

    public NotificationMetrics(MeterRegistry registry) {
        delivered = Counter.builder("agricore.notification.deliveries")
                .description("Notification delivery outcomes")
                .tag("outcome", "sent")
                .register(registry);
        failed = Counter.builder("agricore.notification.deliveries")
                .description("Notification delivery outcomes")
                .tag("outcome", "failed")
                .register(registry);
        duplicates = Counter.builder("agricore.notification.deliveries")
                .description("Notification delivery outcomes")
                .tag("outcome", "duplicate")
                .register(registry);
    }

    public void recordDelivered() { delivered.increment(); }
    public void recordFailed() { failed.increment(); }
    public void recordDuplicate() { duplicates.increment(); }
}
