package com.agricore.assistant.infrastructure.provider;

import com.agricore.assistant.application.model.ChatChunk;
import com.agricore.assistant.application.model.ChatGenerationRequest;
import com.agricore.assistant.application.model.ChatTurn;
import com.agricore.assistant.application.model.ChatTurnRole;
import com.agricore.assistant.application.port.AssistantProviderException;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SpringAiChatProviderTest {

    @Test
    void mapsMessagesDeltasUsageAndTerminalMetadata() {
        RecordingChatModel model = new RecordingChatModel(Flux.just(
                response("Xin ", null, null),
                response("chào", "stop", new DefaultUsage(7, 2))
        ));
        SpringAiChatProvider provider = provider(model, Duration.ofSeconds(2));

        List<ChatChunk> chunks = provider.stream(request()).collectList().block(Duration.ofSeconds(2));

        assertThat(chunks).containsExactly(
                ChatChunk.delta("Xin "),
                ChatChunk.delta("chào"),
                ChatChunk.terminal("stop", 7, 2)
        );
        assertThat(model.prompt.getInstructions()).extracting(Message::getText)
                .containsExactly("You are helpful", "Xin chào");
        assertThat(model.prompt.getInstructions()).extracting(Message::getMessageType)
                .extracting(Object::toString)
                .containsExactly("SYSTEM", "USER");
        OpenAiChatOptions options = (OpenAiChatOptions) model.prompt.getOptions();
        assertThat(options.getModel()).isEqualTo("test-model");
        assertThat(options.getMaxTokens()).isEqualTo(128);
        assertThat(options.getTemperature()).isEqualTo(0.1);
    }

    @Test
    void appliesAbsoluteDeadlineAndMapsProviderErrors() {
        SpringAiChatProvider timeoutProvider = provider(
                new RecordingChatModel(Flux.never()),
                Duration.ofMillis(20)
        );
        assertThatThrownBy(() -> timeoutProvider.stream(request()).collectList().block(Duration.ofSeconds(1)))
                .isInstanceOfSatisfying(AssistantProviderException.class, error ->
                        assertThat(error.getCode()).isEqualTo("AI_PROVIDER_TIMEOUT"));

        SpringAiChatProvider failedProvider = provider(
                new RecordingChatModel(Flux.error(new TimeoutException("provider timeout"))),
                Duration.ofSeconds(2)
        );
        assertThatThrownBy(() -> failedProvider.stream(request()).blockLast())
                .isInstanceOfSatisfying(AssistantProviderException.class, error ->
                        assertThat(error.getCode()).isEqualTo("AI_PROVIDER_TIMEOUT"));
    }

    private SpringAiChatProvider provider(RecordingChatModel model, Duration deadline) {
        return new SpringAiChatProvider(
                "test",
                model,
                request -> OpenAiChatOptions.builder()
                        .model(request.model())
                        .maxTokens(request.maxOutputTokens())
                        .temperature(request.temperature())
                        .build(),
                deadline
        );
    }

    private ChatGenerationRequest request() {
        return new ChatGenerationRequest(
                List.of(
                        new ChatTurn(ChatTurnRole.SYSTEM, "You are helpful"),
                        new ChatTurn(ChatTurnRole.USER, "Xin chào")
                ),
                "test-model",
                128,
                0.1
        );
    }

    private ChatResponse response(String text, String finishReason, DefaultUsage usage) {
        ChatGenerationMetadata metadata = finishReason == null
                ? ChatGenerationMetadata.NULL
                : ChatGenerationMetadata.builder().finishReason(finishReason).build();
        ChatResponseMetadata responseMetadata = usage == null
                ? ChatResponseMetadata.builder().build()
                : ChatResponseMetadata.builder().usage(usage).build();
        return new ChatResponse(
                List.of(new Generation(new AssistantMessage(text), metadata)),
                responseMetadata
        );
    }

    private static final class RecordingChatModel implements ChatModel {

        private final Flux<ChatResponse> responses;
        private Prompt prompt;

        private RecordingChatModel(Flux<ChatResponse> responses) {
            this.responses = responses;
        }

        @Override
        public ChatResponse call(Prompt prompt) {
            throw new UnsupportedOperationException("call is not used by streaming provider");
        }

        @Override
        public Flux<ChatResponse> stream(Prompt prompt) {
            this.prompt = prompt;
            return responses;
        }
    }
}
