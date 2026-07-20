package com.agricore.assistant.application.port;

import java.time.Duration;

public interface AssistantRetentionPolicy {
    Duration archivedConversationRetention();

    Duration auditEventRetention();
}
