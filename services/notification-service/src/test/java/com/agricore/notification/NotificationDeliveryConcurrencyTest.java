package com.agricore.notification;

import com.agricore.notification.api.request.SendNotificationRequest;
import com.agricore.notification.api.response.NotificationResponse;
import com.agricore.notification.application.service.NotificationApplicationService;
import com.agricore.notification.infrastructure.delivery.TestNotificationDeliveryAdapter;
import com.agricore.notification.infrastructure.persistence.NotificationJpaRepository;
import com.agricore.notification.infrastructure.persistence.OutboxJpaRepository;
import com.agricore.notification.infrastructure.persistence.ProcessedEventJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class NotificationDeliveryConcurrencyTest {

    @Autowired
    private NotificationApplicationService service;
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
    void concurrentIdempotentRequestsCreateAndDeliverOnce() throws Exception {
        SendNotificationRequest request = new SendNotificationRequest(
                "EMAIL", "manager@agricore.local", "Harvest completed",
                "Batch HB-1 ready", "corr-concurrent", "delivery-concurrent"
        );
        CountDownLatch start = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> {
                start.await(5, TimeUnit.SECONDS);
                return service.send(request);
            });
            var second = executor.submit(() -> {
                start.await(5, TimeUnit.SECONDS);
                return service.send(request);
            });
            start.countDown();

            NotificationResponse firstResponse = first.get(10, TimeUnit.SECONDS);
            NotificationResponse secondResponse = second.get(10, TimeUnit.SECONDS);

            assertThat(secondResponse.id()).isEqualTo(firstResponse.id());
        }

        assertThat(notificationRepository.count()).isEqualTo(1);
        assertThat(deliveryAdapter.attempts()).isEqualTo(1);
        assertThat(outboxRepository.count()).isEqualTo(2);
    }
}
