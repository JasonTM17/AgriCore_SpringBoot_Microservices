package com.agricore.assistant.infrastructure.retention;

import com.agricore.assistant.infrastructure.configuration.AssistantRetentionProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;

@Component
@ConditionalOnProperty(
        prefix = "agricore.assistant.retention",
        name = "cleanup-enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class AssistantRetentionCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(AssistantRetentionCleanupJob.class);

    private final AssistantRetentionCleanupStore store;
    private final AssistantRetentionCleanupMetrics metrics;
    private final AssistantRetentionProperties properties;
    private final Clock clock;

    public AssistantRetentionCleanupJob(
            AssistantRetentionCleanupStore store,
            AssistantRetentionCleanupMetrics metrics,
            AssistantRetentionProperties properties,
            Clock clock
    ) {
        this.store = store;
        this.metrics = metrics;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(
            fixedDelayString = "${agricore.assistant.retention.cleanup-interval:PT1H}",
            initialDelayString = "${agricore.assistant.retention.cleanup-interval:PT1H}"
    )
    public void purgeExpired() {
        Instant startedAt = clock.instant();
        try {
            AssistantRetentionCleanupStore.CleanupResult result =
                    store.purgeExpired(startedAt, properties.getCleanupBatchSize());
            metrics.record(result);
            if (result.total() > 0) {
                log.info(
                        "Assistant retention cleanup purged generationEvents={}, conversations={}, auditEvents={}",
                        result.generationEvents(),
                        result.conversations(),
                        result.auditEvents()
                );
            }
        } catch (RuntimeException exception) {
            metrics.recordFailure();
            log.error("Assistant retention cleanup failed", exception);
            throw exception;
        }
    }
}
