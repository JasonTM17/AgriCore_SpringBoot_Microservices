package com.agricore.assistant;

import com.agricore.assistant.application.model.ChatChunk;
import com.agricore.assistant.application.model.GenerationSubmissionResult;
import com.agricore.assistant.application.model.ProviderCapabilities;
import com.agricore.assistant.application.port.ChatProvider;
import com.agricore.assistant.domain.model.GenerationStatus;
import com.agricore.assistant.infrastructure.worker.GenerationWorker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import reactor.core.publisher.Flux;
import reactor.core.publisher.SignalType;
import reactor.core.publisher.Sinks;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
        "agricore.assistant.worker.lease-duration=PT10S",
        "agricore.assistant.worker.heartbeat-interval=PT5S"
})
@ActiveProfiles("test")
class GenerationWorkerLocalCancellationIntegrationTest
        extends GenerationStatePersistenceIntegrationTestSupport {

    @MockitoBean
    private ChatProvider chatProvider;

    @Autowired
    private GenerationWorker worker;

    @BeforeEach
    void providerMatchesPersistedSnapshot() {
        when(chatProvider.capabilities())
                .thenReturn(new ProviderCapabilities("openai", true, true, null));
    }

    @Test
    void localSignalCancelsTheProviderWithoutWaitingForTheNextHeartbeat() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID conversationId = insertConversation(owner);
        GenerationSubmissionResult submitted = submit(conversationId, owner, "local-cancel", NOW);
        CountDownLatch providerStarted = new CountDownLatch(1);
        CountDownLatch providerCancelled = new CountDownLatch(1);
        CountDownLatch workerFinished = new CountDownLatch(1);
        when(chatProvider.stream(any())).thenReturn(Flux.defer(() -> {
            providerStarted.countDown();
            return Flux.<ChatChunk>never().doFinally(signal -> {
                if (signal == SignalType.CANCEL) {
                    providerCancelled.countDown();
                }
            });
        }));
        Sinks.Empty<Void> cancellation = Sinks.empty();

        worker.execute(submitted.generation().id(), cancellation.asMono())
                .doFinally(signal -> workerFinished.countDown())
                .subscribe();
        assertThat(providerStarted.await(2, TimeUnit.SECONDS)).isTrue();
        Instant now = Instant.now();
        executionRepository.requestCancellation(
                submitted.generation().id(), conversationId, owner,
                now, now.plus(Duration.ofHours(24)));
        assertThat(cancellation.tryEmitEmpty().isSuccess()).isTrue();

        assertThat(workerFinished.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(providerCancelled.await(1, TimeUnit.SECONDS)).isTrue();
        assertThat(generationRepository.findOwned(
                submitted.generation().id(), conversationId, owner).orElseThrow().status())
                .isEqualTo(GenerationStatus.CANCELLED);
    }
}
