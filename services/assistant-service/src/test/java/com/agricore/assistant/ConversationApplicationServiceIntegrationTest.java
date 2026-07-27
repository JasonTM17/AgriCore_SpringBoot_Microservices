package com.agricore.assistant;

import com.agricore.assistant.application.model.CreateConversationCommand;
import com.agricore.assistant.application.model.PageQuery;
import com.agricore.assistant.application.port.ConversationContextAccess;
import com.agricore.assistant.application.service.ConversationApplicationService;
import com.agricore.assistant.domain.exception.AssistantException;
import com.agricore.assistant.domain.model.AssistantActor;
import com.agricore.assistant.domain.model.AssistantConversation;
import com.agricore.assistant.domain.model.ConversationContextType;
import com.agricore.assistant.domain.model.ConversationStatus;
import com.agricore.farmaccess.FarmAccessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@SpringBootTest
@ActiveProfiles("test")
class ConversationApplicationServiceIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-07-20T02:00:00Z");

    @Autowired
    private ConversationApplicationService service;
    @Autowired
    private JdbcTemplate jdbc;
    @MockitoBean
    private ConversationContextAccess contextAccess;
    @MockitoBean
    private Clock clock;

    @BeforeEach
    void cleanDatabase() {
        jdbc.update("DELETE FROM assistant_audit_events");
        jdbc.update("DELETE FROM conversations");
        clearInvocations(contextAccess);
        org.mockito.Mockito.when(clock.instant()).thenReturn(NOW);
    }

    @Test
    void createNormalizesActorRolesAndPersistsRedactedAudit() {
        UUID ownerId = UUID.randomUUID();
        AssistantConversation conversation = service.create(
                new AssistantActor(ownerId, List.of("ROLE_FARM_MANAGER", "AGRONOMIST", "ROLE_FARM_MANAGER")),
                new CreateConversationCommand("  Vụ mùa 2026  ", ConversationContextType.ENTERPRISE, null)
        );

        assertThat(conversation.ownerUserId()).isEqualTo(ownerId);
        assertThat(conversation.title()).isEqualTo("Vụ mùa 2026");
        assertThat(conversation.roleSnapshot()).containsExactly("AGRONOMIST", "FARM_MANAGER");
        assertThat(conversation.status()).isEqualTo(ConversationStatus.OPEN);
        assertThat(conversation.createdAt()).isEqualTo(NOW);
        assertThat(jdbc.queryForObject(
                "SELECT role_snapshot FROM conversations WHERE id = ?", String.class, conversation.id()))
                .isEqualTo("[\"AGRONOMIST\",\"FARM_MANAGER\"]");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM assistant_audit_events WHERE conversation_id = ?", Integer.class,
                conversation.id())).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT metadata FROM assistant_audit_events WHERE conversation_id = ?", String.class,
                conversation.id())).isNull();
        assertThat(jdbc.queryForObject(
                "SELECT action FROM assistant_audit_events WHERE conversation_id = ?", String.class,
                conversation.id())).isEqualTo("CONVERSATION_CREATED");
    }

    @Test
    void farmConversationRequiresAuthoritativeFarmAccess() {
        UUID farmId = UUID.randomUUID();
        AssistantConversation conversation = service.create(
                actor(),
                new CreateConversationCommand("Farm thread", ConversationContextType.FARM, farmId)
        );

        verify(contextAccess).requireFarmAccess(farmId);
        assertThat(conversation.contextType()).isEqualTo(ConversationContextType.FARM);
        assertThat(conversation.farmId()).isEqualTo(farmId);
    }

    @Test
    void invalidContextIsRejectedBeforeFarmLookupOrPersistence() {
        assertThatThrownBy(() -> service.create(
                actor(),
                new CreateConversationCommand("Invalid", ConversationContextType.FARM, null)
        )).isInstanceOf(AssistantException.class)
                .extracting("code").isEqualTo("INVALID_CONVERSATION_CONTEXT");

        assertThatThrownBy(() -> service.create(
                actor(),
                new CreateConversationCommand("Invalid", ConversationContextType.ENTERPRISE, UUID.randomUUID())
        )).isInstanceOf(AssistantException.class)
                .extracting("code").isEqualTo("INVALID_CONVERSATION_CONTEXT");

        verifyNoInteractions(contextAccess);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM conversations", Integer.class)).isZero();
    }

    @Test
    void deniedFarmAccessLeavesNoConversationOrAuditRow() {
        UUID farmId = UUID.randomUUID();
        doThrow(new FarmAccessException("FARM_ACCESS_DENIED", "denied", 403))
                .when(contextAccess).requireFarmAccess(farmId);

        assertThatThrownBy(() -> service.create(
                actor(), new CreateConversationCommand("Denied", ConversationContextType.FARM, farmId)
        )).isInstanceOf(FarmAccessException.class);

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM conversations", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM assistant_audit_events", Integer.class)).isZero();
    }

    @Test
    void ownerOnlyReadsAndArchiveReturnsNotFoundForOtherSubjects() {
        AssistantConversation conversation = service.create(
                actor(),
                new CreateConversationCommand("Private", ConversationContextType.ENTERPRISE, null)
        );
        AssistantActor other = new AssistantActor(UUID.randomUUID(), List.of("FARM_MANAGER"));

        assertThatThrownBy(() -> service.get(other, conversation.id()))
                .isInstanceOf(AssistantException.class)
                .extracting("code").isEqualTo("CONVERSATION_NOT_FOUND");
        assertThatThrownBy(() -> service.archive(other, conversation.id()))
                .isInstanceOf(AssistantException.class)
                .extracting("code").isEqualTo("CONVERSATION_NOT_FOUND");
        assertThat(jdbc.queryForObject(
                "SELECT status FROM conversations WHERE id = ?", String.class, conversation.id()))
                .isEqualTo("OPEN");
    }

    @Test
    void archiveIsIdempotentRetainsConversationAndAuditsOnlyTransition() {
        AssistantActor owner = actor();
        AssistantConversation conversation = service.create(
                owner,
                new CreateConversationCommand("Archive me", ConversationContextType.ENTERPRISE, null)
        );

        AssistantConversation archived = service.archive(owner, conversation.id());
        AssistantConversation repeated = service.archive(owner, conversation.id());

        assertThat(archived.status()).isEqualTo(ConversationStatus.ARCHIVED);
        assertThat(archived.archivedAt()).isEqualTo(NOW);
        assertThat(archived.purgeAfter()).isEqualTo(NOW.plusSeconds(90 * 24 * 60 * 60L));
        assertThat(repeated.archivedAt()).isEqualTo(archived.archivedAt());
        assertThat(repeated.purgeAfter()).isEqualTo(archived.purgeAfter());
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM assistant_audit_events WHERE conversation_id = ?", Integer.class,
                conversation.id())).isEqualTo(2);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM assistant_audit_events WHERE action = 'CONVERSATION_ARCHIVED'", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void listFiltersByOwnerAndStatusWithStablePageMetadata() {
        AssistantActor owner = actor();
        AssistantConversation open = service.create(
                owner, new CreateConversationCommand("Open", ConversationContextType.ENTERPRISE, null));
        AssistantConversation archived = service.create(
                owner, new CreateConversationCommand("Archived", ConversationContextType.ENTERPRISE, null));
        service.archive(owner, archived.id());

        var openPage = service.list(owner, ConversationStatus.OPEN, new PageQuery(0, 20));
        var archivedPage = service.list(owner, ConversationStatus.ARCHIVED, new PageQuery(0, 20));
        var otherPage = service.list(
                new AssistantActor(UUID.randomUUID(), List.of("FIELD_WORKER")),
                ConversationStatus.OPEN,
                new PageQuery(0, 20));

        assertThat(openPage.content()).extracting(AssistantConversation::id).containsExactly(open.id());
        assertThat(archivedPage.content()).extracting(AssistantConversation::id).containsExactly(archived.id());
        assertThat(otherPage.content()).isEmpty();
        assertThat(openPage.totalElements()).isEqualTo(1);
        assertThat(openPage.totalPages()).isEqualTo(1);
    }

    private AssistantActor actor() {
        return new AssistantActor(UUID.randomUUID(), List.of("FARM_MANAGER"));
    }
}
