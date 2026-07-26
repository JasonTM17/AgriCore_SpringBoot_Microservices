package com.agricore.notification.infrastructure.messaging;

import com.agricore.notification.api.request.UserRegisteredCommand;
import com.agricore.notification.application.service.NotificationApplicationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Drives the listener's envelope parsing and filtering.
 * The fixture is the exact envelope identity-service emits — if the producer
 * changes field names, this fails instead of failing silently on the real topic.
 */
class UserRegisteredKafkaListenerTest {

    private static final String USER_REGISTERED = """
            {
              "eventId": "3f1b0c9e-1111-4a2b-8c3d-9e0f1a2b3c4d",
              "eventType": "UserRegistered.v1",
              "eventVersion": 1,
              "occurredAt": "2026-07-26T10:15:30Z",
              "producer": "identity-service",
              "payload": {
                "userId": "11111111-2222-3333-4444-555555555555",
                "email": "worker@agricore.test",
                "fullName": "Field Worker",
                "roles": ["FIELD_WORKER"],
                "registeredAt": "2026-07-26T10:15:30Z"
              }
            }
            """;

    private static final String FOREIGN_EVENT = """
            {
              "eventId": "8e2c1d0f-2222-4b3c-9d4e-0f1a2b3c4d5e",
              "eventType": "FarmCreated.v1",
              "eventVersion": 1,
              "occurredAt": "2026-07-26T10:16:00Z",
              "producer": "farm-service",
              "payload": {"farmId": "22222222-3333-4444-5555-666666666666"}
            }
            """;

    private final NotificationApplicationService service = mock(NotificationApplicationService.class);
    private final UserRegisteredKafkaListener listener =
            new UserRegisteredKafkaListener(service, new ObjectMapper());

    @Test
    void onMessage_mapsEnvelopeToCommand() {
        listener.onMessage(USER_REGISTERED);

        ArgumentCaptor<UserRegisteredCommand> captor = ArgumentCaptor.forClass(UserRegisteredCommand.class);
        verify(service).recordUserRegistered(captor.capture());

        UserRegisteredCommand command = captor.getValue();
        assertThat(command.eventId()).isEqualTo("3f1b0c9e-1111-4a2b-8c3d-9e0f1a2b3c4d");
        assertThat(command.userId()).isEqualTo("11111111-2222-3333-4444-555555555555");
        assertThat(command.email()).isEqualTo("worker@agricore.test");
        assertThat(command.fullName()).isEqualTo("Field Worker");
        assertThat(command.roles()).containsExactly("FIELD_WORKER");
    }

    @Test
    void onMessage_ignoresForeignEventType() {
        listener.onMessage(FOREIGN_EVENT);

        verify(service, never()).recordUserRegistered(any());
    }

    @Test
    void onMessage_ignoresEnvelopeWithoutPayload() {
        listener.onMessage("""
                {"eventId": "abc", "eventType": "UserRegistered.v1"}
                """);

        verify(service, never()).recordUserRegistered(any());
    }

    @Test
    void onMessage_throwsOnMalformedJsonSoErrorHandlerRoutesToDlt() {
        assertThatThrownBy(() -> listener.onMessage("not json at all"))
                .isInstanceOf(IllegalStateException.class);

        verify(service, never()).recordUserRegistered(any());
    }
}
