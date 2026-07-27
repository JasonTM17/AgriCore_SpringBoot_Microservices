package com.agricore.assistant.application.port;

import com.agricore.assistant.domain.model.AssistantAuditEvent;

public interface AssistantAuditRepository {
    void save(AssistantAuditEvent event);
}
