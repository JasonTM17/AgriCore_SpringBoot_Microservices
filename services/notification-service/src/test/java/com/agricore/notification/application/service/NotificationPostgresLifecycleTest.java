package com.agricore.notification.application.service;

import com.agricore.notification.api.request.SendNotificationRequest;
import com.agricore.notification.api.response.NotificationResponse;
import com.agricore.notification.application.port.NotificationDeliveryResult;
import com.agricore.notification.infrastructure.delivery.TestNotificationDeliveryAdapter;
import com.agricore.notification.infrastructure.persistence.NotificationJpaRepository;
import com.agricore.notification.infrastructure.persistence.OutboxJpaRepository;
import com.agricore.notification.infrastructure.persistence.ProcessedEventJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class NotificationPostgresLifecycleTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("notification_lifecycle_test")
            .withUsername("agricore_test")
            .withPassword("agricore_test");

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.properties.hibernate.dialect",
                () -> "org.hibernate.dialect.PostgreSQLDialect");
    }

    @Autowired
    private NotificationApplicationService applicationService;
    @Autowired
    private NotificationPersistenceService persistenceService;
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
    void concurrentIdempotentRequestsDeliverOnceOnPostgres() throws Exception {
        var request = new SendNotificationRequest(
                "EMAIL", "manager@agricore.local", "Harvest completed", "Batch HB-1 ready",
                "corr-postgres", "delivery-postgres"
        );
        CountDownLatch start = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> sendAfter(start, request));
            var second = executor.submit(() -> sendAfter(start, request));
            start.countDown();

            NotificationResponse firstResponse = first.get(15, TimeUnit.SECONDS);
            NotificationResponse secondResponse = second.get(15, TimeUnit.SECONDS);
            assertThat(secondResponse.id()).isEqualTo(firstResponse.id());
        }

        assertThat(notificationRepository.count()).isEqualTo(1);
        assertThat(deliveryAdapter.attempts()).isEqualTo(1);
        assertThat(outboxRepository.count()).isEqualTo(2);
    }

    @Test
    void deliveryLeasePreventsEarlyClaimAndAllowsStaleRecoveryOnPostgres() {
        var requested = persistenceService.createRequested(new NotificationDraft(
                "EMAIL", "manager@agricore.local", "Harvest completed", "Batch HB-1 ready",
                "corr-lease", null, "DirectApi", "delivery-lease-postgres"
        ));

        assertThat(persistenceService.claimDelivery(requested.getId(), Instant.now().minusSeconds(30)))
                .isPresent();
        assertThat(persistenceService.claimDelivery(requested.getId(), Instant.now().minusSeconds(30)))
                .isEmpty();
        assertThat(persistenceService.claimDelivery(requested.getId(), Instant.now().plusSeconds(1)))
                .isPresent();
    }

    @Test
    void staleWorkerCannotCompleteAfterRecoveryTakesOwnership() {
        var requested = persistenceService.createRequested(new NotificationDraft(
                "EMAIL", "manager@agricore.local", "Harvest completed", "Batch HB-1 ready",
                "corr-fence", null, "DirectApi", "delivery-fence-postgres"
        ));
        var firstClaim = persistenceService.claimDelivery(requested.getId(), Instant.now().minusSeconds(30))
                .orElseThrow();
        assertThat(persistenceService.beginAttempt(requested.getId(), firstClaim.claimId(), 2)).contains(1);
        var recoveredClaim = persistenceService.claimDelivery(requested.getId(), Instant.now().plusSeconds(1))
                .orElseThrow();

        var staleCompletion = persistenceService.completeDelivery(
                requested.getId(), firstClaim.claimId(), NotificationDeliveryResult.sent());
        assertThat(staleCompletion.transitioned()).isFalse();
        assertThat(recoveredClaim.claimId()).isNotEqualTo(firstClaim.claimId());
        assertThat(persistenceService.beginAttempt(requested.getId(), recoveredClaim.claimId(), 2)).contains(2);
        assertThat(persistenceService.beginAttempt(requested.getId(), recoveredClaim.claimId(), 2)).isEmpty();
    }

    private NotificationResponse sendAfter(
            CountDownLatch start,
            SendNotificationRequest request
    ) throws InterruptedException {
        assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
        return applicationService.send(request);
    }
}
