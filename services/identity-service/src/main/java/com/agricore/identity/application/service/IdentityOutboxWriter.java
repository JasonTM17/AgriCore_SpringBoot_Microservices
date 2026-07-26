package com.agricore.identity.application.service;

import com.agricore.common.event.EventTypes;
import com.agricore.identity.domain.exception.IdentityException;
import com.agricore.identity.infrastructure.persistence.OutboxJpaRepository;
import com.agricore.identity.infrastructure.persistence.entity.OutboxEventEntity;
import com.agricore.identity.infrastructure.persistence.entity.RoleEntity;
import com.agricore.identity.infrastructure.persistence.entity.UserEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Service-local transactional outbox writer for identity domain events.
 * Keeps envelope construction out of the authentication orchestrator.
 *
 * <p>The payload carries only addressing data a consumer needs (id, email, name,
 * roles). Password hashes, tokens, and refresh-token material never leave the service.
 */
@Component
public class IdentityOutboxWriter {

    public static final String TOPIC = "agricore.identity.events";
    public static final String PRODUCER = "identity-service";
    public static final String AGGREGATE_TYPE = "User";

    private final OutboxJpaRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public IdentityOutboxWriter(OutboxJpaRepository outboxRepository, ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Enqueues a UserRegistered.v1 envelope in the caller's transaction.
     * A serialization failure aborts the caller so a user is never committed
     * without its registration event.
     */
    public void enqueueUserRegistered(UserEntity user) {
        try {
            ObjectNode payload = objectMapper.createObjectNode();
            payload.put("userId", user.getId().toString());
            payload.put("email", user.getEmail());
            payload.put("fullName", user.getFullName());

            ArrayNode roles = payload.putArray("roles");
            roleCodes(user).forEach(roles::add);

            payload.put("registeredAt", user.getCreatedAt().toString());

            ObjectNode envelope = objectMapper.createObjectNode();
            envelope.put("eventId", UUID.randomUUID().toString());
            envelope.put("eventType", EventTypes.USER_REGISTERED);
            envelope.put("eventVersion", 1);
            envelope.put("occurredAt", Instant.now().toString());
            envelope.put("producer", PRODUCER);
            envelope.set("payload", payload);

            outboxRepository.save(OutboxEventEntity.create(
                    AGGREGATE_TYPE,
                    user.getId().toString(),
                    EventTypes.USER_REGISTERED,
                    TOPIC,
                    objectMapper.writeValueAsString(envelope)
            ));
        } catch (Exception ex) {
            throw new IdentityException("OUTBOX_WRITE_FAILED", "Failed to write registration event", 500);
        }
    }

    /** Sorted so the event payload is deterministic regardless of role fetch order. */
    private static List<String> roleCodes(UserEntity user) {
        if (user.getRoles() == null) {
            return List.of();
        }
        return user.getRoles().stream()
                .map(RoleEntity::getCode)
                .sorted(Comparator.naturalOrder())
                .toList();
    }
}
