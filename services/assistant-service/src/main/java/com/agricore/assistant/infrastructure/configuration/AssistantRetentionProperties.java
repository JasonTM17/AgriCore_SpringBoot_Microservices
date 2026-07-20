package com.agricore.assistant.infrastructure.configuration;

import com.agricore.assistant.application.port.AssistantRetentionPolicy;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "agricore.assistant.retention")
public class AssistantRetentionProperties implements AssistantRetentionPolicy {

    private Duration archivedConversation = Duration.ofDays(90);
    private Duration auditEvent = Duration.ofDays(365);
    private Duration generationEvent = Duration.ofHours(24);

    public Duration getArchivedConversation() {
        return archivedConversation;
    }

    @Override
    public Duration archivedConversationRetention() {
        return archivedConversation;
    }

    public void setArchivedConversation(Duration archivedConversation) {
        this.archivedConversation = requirePositive(archivedConversation, "archivedConversation");
    }

    public Duration getAuditEvent() {
        return auditEvent;
    }

    @Override
    public Duration auditEventRetention() {
        return auditEvent;
    }

    public void setAuditEvent(Duration auditEvent) {
        this.auditEvent = requirePositive(auditEvent, "auditEvent");
    }

    public Duration getGenerationEvent() {
        return generationEvent;
    }

    @Override
    public Duration generationEventRetention() {
        return generationEvent;
    }

    public void setGenerationEvent(Duration generationEvent) {
        this.generationEvent = requirePositive(generationEvent, "generationEvent");
    }

    private static Duration requirePositive(Duration value, String name) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }
}
