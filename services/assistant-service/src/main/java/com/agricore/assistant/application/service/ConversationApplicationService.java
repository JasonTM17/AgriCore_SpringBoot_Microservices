package com.agricore.assistant.application.service;

import com.agricore.assistant.application.model.ConversationArchiveResult;
import com.agricore.assistant.application.model.CreateConversationCommand;
import com.agricore.assistant.application.model.PageQuery;
import com.agricore.assistant.application.model.PageResult;
import com.agricore.assistant.application.port.AssistantAuditRepository;
import com.agricore.assistant.application.port.AssistantRetentionPolicy;
import com.agricore.assistant.application.port.ConversationContextAccess;
import com.agricore.assistant.application.port.ConversationRepository;
import com.agricore.assistant.domain.exception.AssistantException;
import com.agricore.assistant.domain.model.AssistantActor;
import com.agricore.assistant.domain.model.AssistantAuditEvent;
import com.agricore.assistant.domain.model.AssistantConversation;
import com.agricore.assistant.domain.model.AssistantMessage;
import com.agricore.assistant.domain.model.ConversationContextType;
import com.agricore.assistant.domain.model.ConversationStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
public class ConversationApplicationService {

    private static final int MAX_TITLE_LENGTH = 200;

    private final ConversationRepository conversationRepository;
    private final AssistantAuditRepository auditRepository;
    private final ConversationContextAccess contextAccess;
    private final AssistantRetentionPolicy retentionPolicy;
    private final Clock clock;

    public ConversationApplicationService(
            ConversationRepository conversationRepository,
            AssistantAuditRepository auditRepository,
            ConversationContextAccess contextAccess,
            AssistantRetentionPolicy retentionPolicy,
            Clock clock
    ) {
        this.conversationRepository = conversationRepository;
        this.auditRepository = auditRepository;
        this.contextAccess = contextAccess;
        this.retentionPolicy = retentionPolicy;
        this.clock = clock;
    }

    @Transactional
    public AssistantConversation create(AssistantActor actor, CreateConversationCommand command) {
        String title = validateTitle(command.title());
        validateContext(command.contextType(), command.farmId());
        if (command.contextType() == ConversationContextType.FARM) {
            contextAccess.requireFarmAccess(command.farmId());
        }

        Instant now = clock.instant();
        AssistantConversation conversation = new AssistantConversation(
                UUID.randomUUID(),
                actor.subject(),
                title,
                command.contextType(),
                command.farmId(),
                ConversationStatus.OPEN,
                actor.roles(),
                0,
                0,
                now,
                now,
                null,
                null
        );
        AssistantConversation saved = conversationRepository.save(conversation);
        auditRepository.save(AssistantAuditEvent.success(
                actor.subject(), actor.subject(), saved.farmId(), saved.id(),
                "CONVERSATION_CREATED", now, now.plus(retentionPolicy.auditEventRetention())
        ));
        return saved;
    }

    @Transactional(readOnly = true)
    public AssistantConversation get(AssistantActor actor, UUID conversationId) {
        return conversationRepository.findOwned(conversationId, actor.subject())
                .orElseThrow(AssistantException::notFound);
    }

    @Transactional(readOnly = true)
    public PageResult<AssistantConversation> list(
            AssistantActor actor,
            ConversationStatus status,
            PageQuery pageQuery
    ) {
        ConversationStatus effectiveStatus = status == null ? ConversationStatus.OPEN : status;
        return conversationRepository.findOwnedByStatus(actor.subject(), effectiveStatus, pageQuery);
    }

    @Transactional(readOnly = true)
    public PageResult<AssistantMessage> messages(
            AssistantActor actor,
            UUID conversationId,
            PageQuery pageQuery
    ) {
        get(actor, conversationId);
        return conversationRepository.findMessages(conversationId, actor.subject(), pageQuery);
    }

    @Transactional
    public AssistantConversation archive(AssistantActor actor, UUID conversationId) {
        Instant now = clock.instant();
        ConversationArchiveResult result = conversationRepository.archiveOwned(
                conversationId,
                actor.subject(),
                now,
                now.plus(retentionPolicy.archivedConversationRetention())
        );
        AssistantConversation conversation = result.conversation();
        if (conversation == null) {
            throw AssistantException.notFound();
        }
        if (result.changed()) {
            auditRepository.save(AssistantAuditEvent.success(
                    actor.subject(), actor.subject(), conversation.farmId(), conversation.id(),
                    "CONVERSATION_ARCHIVED", now, now.plus(retentionPolicy.auditEventRetention())
            ));
        }
        return conversation;
    }

    private static String validateTitle(String rawTitle) {
        if (rawTitle == null) {
            throw AssistantException.invalidTitle();
        }
        String title = rawTitle.strip();
        if (title.isEmpty() || title.length() > MAX_TITLE_LENGTH) {
            throw AssistantException.invalidTitle();
        }
        return title;
    }

    private static void validateContext(ConversationContextType contextType, UUID farmId) {
        if (contextType == null
                || (contextType == ConversationContextType.FARM && farmId == null)
                || (contextType == ConversationContextType.ENTERPRISE && farmId != null)) {
            throw AssistantException.invalidContext();
        }
    }
}
