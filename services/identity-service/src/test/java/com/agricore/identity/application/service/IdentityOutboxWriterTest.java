package com.agricore.identity.application.service;

import com.agricore.identity.infrastructure.persistence.OutboxJpaRepository;
import com.agricore.identity.infrastructure.persistence.entity.OutboxEventEntity;
import com.agricore.identity.infrastructure.persistence.entity.RoleEntity;
import com.agricore.identity.infrastructure.persistence.entity.UserEntity;
import com.agricore.identity.domain.model.UserStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Locks the UserRegistered.v1 envelope contract. Consumers parse these exact
 * field names, so a change here is a breaking change for notification-service.
 */
class IdentityOutboxWriterTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static RoleEntity role(String code) {
        RoleEntity r = new RoleEntity();
        r.setId(UUID.randomUUID());
        r.setCode(code);
        r.setDescription(code);
        r.setCreatedAt(Instant.now());
        return r;
    }

    private static UserEntity user(Set<RoleEntity> roles) {
        UserEntity u = new UserEntity();
        u.setId(UUID.fromString("11111111-2222-3333-4444-555555555555"));
        u.setEmail("worker@agricore.test");
        u.setPasswordHash("$2a$04$notarealhash");
        u.setFullName("Field Worker");
        u.setStatus(UserStatus.ACTIVE);
        u.setFailedLoginCount(0);
        u.setCreatedAt(Instant.parse("2026-07-26T10:15:30Z"));
        u.setUpdatedAt(Instant.parse("2026-07-26T10:15:30Z"));
        u.setRoles(roles);
        return u;
    }

    private static OutboxEventEntity capture(UserEntity user) {
        OutboxJpaRepository repository = mock(OutboxJpaRepository.class);
        new IdentityOutboxWriter(repository, MAPPER).enqueueUserRegistered(user);

        ArgumentCaptor<OutboxEventEntity> captor = ArgumentCaptor.forClass(OutboxEventEntity.class);
        verify(repository).save(captor.capture());
        return captor.getValue();
    }

    @Test
    void enqueueUserRegistered_writesRowRoutedToIdentityTopic() {
        OutboxEventEntity row = capture(user(Set.of(role("FIELD_WORKER"))));

        assertThat(row.getAggregateType()).isEqualTo("User");
        assertThat(row.getAggregateId()).isEqualTo("11111111-2222-3333-4444-555555555555");
        assertThat(row.getEventType()).isEqualTo("UserRegistered.v1");
        assertThat(row.getTopic()).isEqualTo("agricore.identity.events");
        assertThat(row.getPublishedAt()).isNull();
        assertThat(row.getPublishAttempts()).isZero();
    }

    @Test
    void enqueueUserRegistered_buildsExactEnvelope() throws Exception {
        JsonNode envelope = MAPPER.readTree(capture(user(Set.of(role("FIELD_WORKER")))).getPayload());

        assertThat(envelope.get("eventType").asText()).isEqualTo("UserRegistered.v1");
        assertThat(envelope.get("eventVersion").asInt()).isEqualTo(1);
        assertThat(envelope.get("producer").asText()).isEqualTo("identity-service");
        assertThat(UUID.fromString(envelope.get("eventId").asText())).isNotNull();
        assertThat(Instant.parse(envelope.get("occurredAt").asText())).isNotNull();

        JsonNode payload = envelope.get("payload");
        assertThat(payload.get("userId").asText()).isEqualTo("11111111-2222-3333-4444-555555555555");
        assertThat(payload.get("email").asText()).isEqualTo("worker@agricore.test");
        assertThat(payload.get("fullName").asText()).isEqualTo("Field Worker");
        assertThat(payload.get("registeredAt").asText()).isEqualTo("2026-07-26T10:15:30Z");
        assertThat(payload.get("roles")).hasSize(1);
        assertThat(payload.get("roles").get(0).asText()).isEqualTo("FIELD_WORKER");
    }

    @Test
    void enqueueUserRegistered_neverLeaksCredentialMaterial() {
        String payload = capture(user(Set.of(role("FIELD_WORKER")))).getPayload();

        assertThat(payload).doesNotContain("$2a$04$notarealhash");
        assertThat(payload.toLowerCase()).doesNotContain("passwordhash");
        assertThat(payload.toLowerCase()).doesNotContain("password");
        assertThat(payload.toLowerCase()).doesNotContain("token");
    }

    @Test
    void enqueueUserRegistered_sortsRolesForDeterministicPayload() throws Exception {
        Set<RoleEntity> unordered = new LinkedHashSet<>();
        unordered.add(role("WAREHOUSE_MANAGER"));
        unordered.add(role("AGRONOMIST"));
        unordered.add(role("FIELD_WORKER"));

        JsonNode roles = MAPPER.readTree(capture(user(unordered)).getPayload())
                .get("payload")
                .get("roles");

        assertThat(roles).hasSize(3);
        assertThat(roles.get(0).asText()).isEqualTo("AGRONOMIST");
        assertThat(roles.get(1).asText()).isEqualTo("FIELD_WORKER");
        assertThat(roles.get(2).asText()).isEqualTo("WAREHOUSE_MANAGER");
    }
}
