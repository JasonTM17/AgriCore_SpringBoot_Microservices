package com.agricore.notification.application.service;

import com.agricore.notification.api.request.UserRegisteredCommand;
import com.agricore.notification.infrastructure.persistence.NotificationJpaRepository;
import com.agricore.notification.infrastructure.persistence.entity.NotificationEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A redelivered UserRegistered event must not create a second welcome notification.
 */
@SpringBootTest
@ActiveProfiles("test")
class UserRegisteredIdempotencyTest {

    @Autowired
    private NotificationApplicationService service;

    @Autowired
    private NotificationJpaRepository notificationRepository;

    private static UserRegisteredCommand command(String eventId, String email) {
        return new UserRegisteredCommand(
                eventId,
                UUID.randomUUID().toString(),
                email,
                "Field Worker",
                List.of("FIELD_WORKER")
        );
    }

    private List<NotificationEntity> notificationsFor(String eventId) {
        return notificationRepository.findAll().stream()
                .filter(n -> eventId.equals(n.getCorrelationId()))
                .toList();
    }

    @Test
    void recordUserRegistered_writesWelcomeNotificationOnce() {
        String eventId = UUID.randomUUID().toString();

        service.recordUserRegistered(command(eventId, "first@agricore.test"));

        List<NotificationEntity> written = notificationsFor(eventId);
        assertThat(written).hasSize(1);

        NotificationEntity n = written.get(0);
        assertThat(n.getChannel()).isEqualTo("EMAIL");
        assertThat(n.getRecipient()).isEqualTo("first@agricore.test");
        assertThat(n.getSubject()).isEqualTo("Welcome to AgriCore");
        assertThat(n.getStatus()).isEqualTo("SENT");
        assertThat(n.getBody()).contains("Field Worker").contains("FIELD_WORKER");
    }

    @Test
    void recordUserRegistered_replayOfSameEventIdIsIgnored() {
        String eventId = UUID.randomUUID().toString();
        UserRegisteredCommand cmd = command(eventId, "replay@agricore.test");

        service.recordUserRegistered(cmd);
        long afterFirst = notificationRepository.count();

        service.recordUserRegistered(cmd);

        assertThat(notificationsFor(eventId)).hasSize(1);
        assertThat(notificationRepository.count()).isEqualTo(afterFirst);
    }

    @Test
    void recordUserRegistered_distinctEventIdsEachProduceANotification() {
        String firstEvent = UUID.randomUUID().toString();
        String secondEvent = UUID.randomUUID().toString();

        service.recordUserRegistered(command(firstEvent, "one@agricore.test"));
        service.recordUserRegistered(command(secondEvent, "two@agricore.test"));

        assertThat(notificationsFor(firstEvent)).hasSize(1);
        assertThat(notificationsFor(secondEvent)).hasSize(1);
    }
}
