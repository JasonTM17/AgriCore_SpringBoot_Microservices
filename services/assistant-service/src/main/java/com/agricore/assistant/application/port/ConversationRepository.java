package com.agricore.assistant.application.port;

import com.agricore.assistant.application.model.ConversationArchiveResult;
import com.agricore.assistant.application.model.PageQuery;
import com.agricore.assistant.application.model.PageResult;
import com.agricore.assistant.domain.model.AssistantConversation;
import com.agricore.assistant.domain.model.AssistantMessage;
import com.agricore.assistant.domain.model.ConversationStatus;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface ConversationRepository {
    AssistantConversation save(AssistantConversation conversation);

    Optional<AssistantConversation> findOwned(UUID conversationId, UUID ownerUserId);

    PageResult<AssistantConversation> findOwnedByStatus(
            UUID ownerUserId,
            ConversationStatus status,
            PageQuery pageQuery
    );

    PageResult<AssistantMessage> findMessages(
            UUID conversationId,
            UUID ownerUserId,
            PageQuery pageQuery
    );

    ConversationArchiveResult archiveOwned(
            UUID conversationId,
            UUID ownerUserId,
            Instant archivedAt,
            Instant purgeAfter
    );
}
