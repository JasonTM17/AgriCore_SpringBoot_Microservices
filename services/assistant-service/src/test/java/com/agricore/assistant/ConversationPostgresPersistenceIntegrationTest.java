package com.agricore.assistant;

import com.agricore.assistant.application.model.CreateConversationCommand;
import com.agricore.assistant.application.model.PageQuery;
import com.agricore.assistant.application.port.ConversationContextAccess;
import com.agricore.assistant.application.service.ConversationApplicationService;
import com.agricore.assistant.domain.model.AssistantActor;
import com.agricore.assistant.domain.model.ConversationContextType;
import com.agricore.assistant.domain.model.ConversationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
class ConversationPostgresPersistenceIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-07-20T02:00:00Z");

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("agricore_assistant")
            .withUsername("agricore")
            .withPassword("agricore_test");

    @Autowired
    private ConversationApplicationService service;
    @Autowired
    private JdbcTemplate jdbc;
    @MockitoBean
    private ConversationContextAccess contextAccess;
    @MockitoBean
    private Clock clock;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    @BeforeEach
    void cleanDatabase() {
        jdbc.update("DELETE FROM assistant_audit_events");
        jdbc.update("DELETE FROM conversations");
        when(clock.instant()).thenReturn(NOW);
    }

    @Test
    void ownedConversationLifecycleUsesPostgresMappingsAndQueries() {
        AssistantActor owner = new AssistantActor(UUID.randomUUID(), List.of("FARM_MANAGER"));
        var conversation = service.create(
                owner,
                new CreateConversationCommand("Postgres thread", ConversationContextType.ENTERPRISE, null)
        );

        assertThat(service.list(owner, ConversationStatus.OPEN, new PageQuery(0, 10)).content())
                .extracting(value -> value.id()).containsExactly(conversation.id());

        var archived = service.archive(owner, conversation.id());

        assertThat(archived.status()).isEqualTo(ConversationStatus.ARCHIVED);
        assertThat(service.list(owner, ConversationStatus.ARCHIVED, new PageQuery(0, 10)).content())
                .extracting(value -> value.id()).containsExactly(conversation.id());
        assertThat(jdbc.queryForObject(
                "SELECT version FROM conversations WHERE id = ?", Long.class, conversation.id()))
                .isEqualTo(1L);
    }
}
