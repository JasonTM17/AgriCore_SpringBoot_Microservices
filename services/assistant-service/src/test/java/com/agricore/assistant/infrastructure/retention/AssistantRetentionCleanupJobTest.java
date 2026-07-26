package com.agricore.assistant.infrastructure.retention;

import com.agricore.assistant.infrastructure.configuration.AssistantRetentionProperties;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AssistantRetentionCleanupJobTest {

    private static final Instant NOW = Instant.parse("2026-07-26T03:00:00Z");

    private final AssistantRetentionCleanupStore store =
            mock(AssistantRetentionCleanupStore.class);
    private final AssistantRetentionCleanupMetrics metrics =
            mock(AssistantRetentionCleanupMetrics.class);
    private final AssistantRetentionProperties properties =
            new AssistantRetentionProperties();
    private final AssistantRetentionCleanupJob job =
            new AssistantRetentionCleanupJob(
                    store,
                    metrics,
                    properties,
                    Clock.fixed(NOW, ZoneOffset.UTC)
            );

    @Test
    void recordsCommittedCleanupCounts() {
        properties.setCleanupBatchSize(25);
        var result = new AssistantRetentionCleanupStore.CleanupResult(3, 2, 1);
        when(store.purgeExpired(NOW, 25)).thenReturn(result);

        job.purgeExpired();

        verify(metrics).record(result);
    }

    @Test
    void reportsAndPropagatesCleanupFailures() {
        RuntimeException failure = new RuntimeException("database unavailable");
        when(store.purgeExpired(NOW, properties.getCleanupBatchSize()))
                .thenThrow(failure);

        assertThatThrownBy(job::purgeExpired).isSameAs(failure);
        verify(metrics).recordFailure();
    }
}
