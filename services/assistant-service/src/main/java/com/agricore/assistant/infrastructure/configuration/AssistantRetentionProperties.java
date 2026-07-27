package com.agricore.assistant.infrastructure.configuration;

import com.agricore.assistant.application.port.AssistantRetentionPolicy;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "agricore.assistant.retention")
public class AssistantRetentionProperties implements AssistantRetentionPolicy {

    private Duration archivedConversation = Duration.ofDays(90);
    private Duration auditEvent = Duration.ofDays(365);
    private Duration generationEvent = Duration.ofHours(24);
    private boolean cleanupEnabled = true;
    private Duration cleanupInterval = Duration.ofHours(1);
    private int cleanupBatchSize = 250;

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

    public boolean isCleanupEnabled() {
        return cleanupEnabled;
    }

    public void setCleanupEnabled(boolean cleanupEnabled) {
        this.cleanupEnabled = cleanupEnabled;
    }

    public Duration getCleanupInterval() {
        return cleanupInterval;
    }

    public void setCleanupInterval(Duration cleanupInterval) {
        this.cleanupInterval = requirePositive(cleanupInterval, "cleanupInterval");
    }

    public int getCleanupBatchSize() {
        return cleanupBatchSize;
    }

    public void setCleanupBatchSize(int cleanupBatchSize) {
        if (cleanupBatchSize < 1 || cleanupBatchSize > 10_000) {
            throw new IllegalArgumentException("cleanupBatchSize must be between 1 and 10000");
        }
        this.cleanupBatchSize = cleanupBatchSize;
    }

    private static Duration requirePositive(Duration value, String name) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }
}
