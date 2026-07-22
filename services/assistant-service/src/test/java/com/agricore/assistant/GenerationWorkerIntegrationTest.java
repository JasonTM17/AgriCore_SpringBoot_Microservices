package com.agricore.assistant;

import com.agricore.assistant.application.model.ChatChunk;
import com.agricore.assistant.application.model.ChatGenerationRequest;
import com.agricore.assistant.application.model.ChatTurnRole;
import com.agricore.assistant.application.model.GenerationSubmissionResult;
import com.agricore.assistant.application.model.ProviderCapabilities;
import com.agricore.assistant.application.model.ToolEvidenceCollection;
import com.agricore.assistant.application.model.ToolEvidenceSnapshot;
import com.agricore.assistant.application.model.ToolFact;
import com.agricore.assistant.application.model.ToolSource;
import com.agricore.assistant.application.port.AssistantProviderException;
import com.agricore.assistant.application.port.ChatProvider;
import com.agricore.assistant.domain.model.AssistantGenerationEvent;
import com.agricore.assistant.domain.model.GenerationEventType;
import com.agricore.assistant.domain.model.GenerationStatus;
import com.agricore.assistant.infrastructure.worker.GenerationWorker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import reactor.core.publisher.Flux;
import reactor.core.publisher.SignalType;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
        "agricore.assistant.worker.lease-duration=PT1S",
        "agricore.assistant.worker.heartbeat-interval=PT0.05S"
})
@ActiveProfiles("test")
class GenerationWorkerIntegrationTest extends GenerationStatePersistenceIntegrationTestSupport {

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
    void streamsBoundedReplayEventsAndPersistsTheExactCompletedResponse() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID conversationId = insertConversation(owner);
        GenerationSubmissionResult submitted = submit(conversationId, owner, "worker-complete", NOW);
        String response = "x".repeat(7_999) + "🌱" + "y".repeat(8_000) + "crop";
        when(chatProvider.stream(any())).thenReturn(Flux.just(
                ChatChunk.delta(response),
                ChatChunk.terminal("STOP", 12, 4)
        ));

        worker.execute(submitted.generation().id()).block(Duration.ofSeconds(5));

        var completed = generationRepository.findOwned(
                submitted.generation().id(), conversationId, owner).orElseThrow();
        assertThat(completed.status()).isEqualTo(GenerationStatus.COMPLETED);
        assertThat(completed.inputTokens()).isEqualTo(12L);
        assertThat(completed.outputTokens()).isEqualTo(4L);
        assertThat(jdbc.queryForObject(
                "SELECT content FROM conversation_messages WHERE generation_id = ? AND role = 'ASSISTANT'",
                String.class,
                submitted.generation().id()
        )).isEqualTo(response);

        List<AssistantGenerationEvent> events = events(submitted, owner);
        assertThat(events).extracting(AssistantGenerationEvent::eventType)
                .containsExactly(
                        GenerationEventType.STATUS,
                        GenerationEventType.STATUS,
                        GenerationEventType.DELTA,
                        GenerationEventType.DELTA,
                        GenerationEventType.DELTA,
                        GenerationEventType.COMPLETED
                );
        String replayed = events.stream()
                .filter(event -> event.eventType() == GenerationEventType.DELTA)
                .map(event -> readDelta(event.payload()))
                .collect(Collectors.joining());
        assertThat(replayed).isEqualTo(response);

        ArgumentCaptor<ChatGenerationRequest> request = ArgumentCaptor.forClass(ChatGenerationRequest.class);
        verify(chatProvider).stream(request.capture());
        assertThat(request.getValue().model()).isEqualTo("gpt-test");
        assertThat(request.getValue().turns()).hasSize(2);
        assertThat(request.getValue().turns().getFirst().role()).isEqualTo(ChatTurnRole.SYSTEM);
        assertThat(request.getValue().turns().getFirst().content())
                .contains("AgriCore read-only assistant")
                .doesNotContain("UNTRUSTED_TOOL_DATA_JSONL_BEGIN");
        assertThat(request.getValue().turns().getLast().role()).isEqualTo(ChatTurnRole.USER);
        assertThat(request.getValue().turns().getLast().content()).isEqualTo("How is the crop?");
    }

    @Test
    void mapsProviderFailureToASafeTerminalCodeWithoutPersistingRawDetails() {
        UUID owner = UUID.randomUUID();
        UUID conversationId = insertConversation(owner);
        GenerationSubmissionResult submitted = submit(conversationId, owner, "worker-failure", NOW);
        when(chatProvider.stream(any())).thenReturn(Flux.error(
                AssistantProviderException.rateLimited(new IllegalStateException("api-key sk-secret-value"))));

        worker.execute(submitted.generation().id()).block(Duration.ofSeconds(5));

        var failed = generationRepository.findOwned(
                submitted.generation().id(), conversationId, owner).orElseThrow();
        assertThat(failed.status()).isEqualTo(GenerationStatus.FAILED);
        assertThat(failed.errorCode()).isEqualTo("AI_PROVIDER_RATE_LIMITED");
        String payload = jdbc.queryForObject(
                "SELECT payload FROM generation_events WHERE generation_id = ? AND event_type = 'ERROR'",
                String.class,
                submitted.generation().id()
        );
        assertThat(payload).contains("AI_PROVIDER_RATE_LIMITED")
                .doesNotContain("sk-secret-value", "api-key");
    }

    @Test
    void releasesToolBackedOutputOnlyAfterAuthorizedCitationValidation() {
        UUID owner = UUID.randomUUID();
        UUID conversationId = insertConversation(owner);
        GenerationSubmissionResult submitted = submit(
                conversationId,
                owner,
                "worker-safe-citation",
                NOW,
                collectedFarmEvidence()
        );
        when(chatProvider.stream(any())).thenReturn(Flux.just(
                ChatChunk.delta("Trang trại đang hoạt động "),
                ChatChunk.delta("[FARM-1]."),
                ChatChunk.terminal("STOP", 15, 6)
        ));

        worker.execute(submitted.generation().id()).block(Duration.ofSeconds(5));

        var completed = generationRepository.findOwned(
                submitted.generation().id(), conversationId, owner).orElseThrow();
        assertThat(completed.status()).isEqualTo(GenerationStatus.COMPLETED);
        String replayed = events(submitted, owner).stream()
                .filter(event -> event.eventType() == GenerationEventType.DELTA)
                .map(event -> readDelta(event.payload()))
                .collect(Collectors.joining());
        assertThat(replayed).isEqualTo("Trang trại đang hoạt động [FARM-1].");
    }

    @Test
    void refusesUnauthorizedToolCitationWithoutPersistingProviderOutput() {
        UUID owner = UUID.randomUUID();
        UUID conversationId = insertConversation(owner);
        GenerationSubmissionResult submitted = submit(
                conversationId,
                owner,
                "worker-unsafe-citation",
                NOW,
                collectedFarmEvidence()
        );
        when(chatProvider.stream(any())).thenReturn(Flux.just(
                ChatChunk.delta("Lô đất khác đang hoạt động [PLOT-9]."),
                ChatChunk.terminal("STOP", 15, 6)
        ));

        worker.execute(submitted.generation().id()).block(Duration.ofSeconds(5));

        var failed = generationRepository.findOwned(
                submitted.generation().id(), conversationId, owner).orElseThrow();
        assertThat(failed.status()).isEqualTo(GenerationStatus.FAILED);
        assertThat(failed.errorCode()).isEqualTo("AI_OUTPUT_CITATION_UNAUTHORIZED");
        assertThat(events(submitted, owner)).extracting(AssistantGenerationEvent::eventType)
                .containsExactly(
                        GenerationEventType.STATUS,
                        GenerationEventType.STATUS,
                        GenerationEventType.ERROR
                );
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM conversation_messages WHERE generation_id = ? AND role = 'ASSISTANT'",
                Integer.class,
                submitted.generation().id()
        )).isZero();
        Map<String, Object> audit = jdbc.queryForMap(
                """
                        SELECT outcome, reason_code
                        FROM assistant_audit_events
                        WHERE generation_id = ? AND action = 'GENERATION_OUTPUT_DECISION'
                        """,
                submitted.generation().id()
        );
        assertThat(audit.get("outcome")).isEqualTo("DENIED");
        assertThat(audit.get("reason_code")).isEqualTo("AI_OUTPUT_CITATION_UNAUTHORIZED");
    }

    @Test
    void heartbeatDetectsCrossNodeCancellationAndCancelsASilentProvider() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID conversationId = insertConversation(owner);
        GenerationSubmissionResult submitted = submit(conversationId, owner, "worker-cancel", NOW);
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

        worker.execute(submitted.generation().id())
                .doFinally(signal -> workerFinished.countDown())
                .subscribe();
        assertThat(providerStarted.await(2, TimeUnit.SECONDS)).isTrue();
        executionRepository.requestCancellation(
                submitted.generation().id(), conversationId, owner, at(1), expiresAt(1));

        assertThat(workerFinished.await(3, TimeUnit.SECONDS)).isTrue();
        assertThat(providerCancelled.await(1, TimeUnit.SECONDS)).isTrue();
        assertThat(generationRepository.findOwned(
                submitted.generation().id(), conversationId, owner).orElseThrow().status())
                .isEqualTo(GenerationStatus.CANCELLED);
    }

    private String readDelta(String payload) {
        try {
            return objectMapper.readTree(payload).get("delta").asText();
        } catch (Exception error) {
            throw new AssertionError("delta payload must be valid JSON", error);
        }
    }

    private ToolEvidenceCollection collectedFarmEvidence() {
        return ToolEvidenceCollection.collected(
                new ToolEvidenceSnapshot(List.of(
                        new ToolFact("FARM-1", ToolSource.FARM, Map.of("status", "ACTIVE"))
                )),
                5
        );
    }
}
