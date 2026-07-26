package com.agricore.identity.application.service;

import com.agricore.identity.TestRedisConfig;
import com.agricore.identity.api.request.RegisterRequest;
import com.agricore.identity.domain.exception.IdentityException;
import com.agricore.identity.infrastructure.persistence.OutboxJpaRepository;
import com.agricore.identity.infrastructure.persistence.entity.OutboxEventEntity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Proves the registration transaction and its outbox row commit together:
 * one accepted registration writes exactly one event, and a rejected one writes none.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestRedisConfig.class)
class AuthRegistrationOutboxIntegrationTest {

    @Autowired
    private AuthApplicationService authService;

    @Autowired
    private OutboxJpaRepository outboxRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private List<OutboxEventEntity> eventsFor(String userId) {
        return outboxRepository.findAll().stream()
                .filter(e -> userId.equals(e.getAggregateId()))
                .toList();
    }

    @Test
    void register_writesExactlyOneUserRegisteredEvent() throws Exception {
        String email = "outbox" + System.nanoTime() + "@agricore.test";

        String userId = authService.register(
                new RegisterRequest(email, "Secret123!", "Outbox User")
        ).id().toString();

        List<OutboxEventEntity> events = eventsFor(userId);
        assertThat(events).hasSize(1);

        OutboxEventEntity row = events.get(0);
        assertThat(row.getEventType()).isEqualTo("UserRegistered.v1");
        assertThat(row.getTopic()).isEqualTo("agricore.identity.events");
        assertThat(row.getPublishedAt()).isNull();

        JsonNode payload = objectMapper.readTree(row.getPayload()).get("payload");
        assertThat(payload.get("userId").asText()).isEqualTo(userId);
        assertThat(payload.get("email").asText()).isEqualTo(email);
        assertThat(payload.get("roles").get(0).asText()).isEqualTo("FIELD_WORKER");
    }

    @Test
    void register_duplicateEmail_writesNoAdditionalEvent() {
        String email = "dupoutbox" + System.nanoTime() + "@agricore.test";
        RegisterRequest request = new RegisterRequest(email, "Secret123!", "Dup Outbox");

        String userId = authService.register(request).id().toString();
        long totalAfterFirst = outboxRepository.count();

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(IdentityException.class);

        assertThat(eventsFor(userId)).hasSize(1);
        assertThat(outboxRepository.count()).isEqualTo(totalAfterFirst);
    }
}
