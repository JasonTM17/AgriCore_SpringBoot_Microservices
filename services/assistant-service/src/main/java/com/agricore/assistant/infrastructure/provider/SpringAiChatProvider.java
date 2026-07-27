package com.agricore.assistant.infrastructure.provider;

import com.agricore.assistant.application.model.ChatChunk;
import com.agricore.assistant.application.model.ChatGenerationRequest;
import com.agricore.assistant.application.model.ChatTurn;
import com.agricore.assistant.application.model.ProviderCapabilities;
import com.agricore.assistant.application.port.ChatProvider;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

public final class SpringAiChatProvider implements ChatProvider {

    private final String providerName;
    private final ChatModel chatModel;
    private final Function<ChatGenerationRequest, ChatOptions> optionsFactory;
    private final Duration maxGenerationDuration;

    SpringAiChatProvider(
            String providerName,
            ChatModel chatModel,
            Function<ChatGenerationRequest, ChatOptions> optionsFactory,
            Duration maxGenerationDuration
    ) {
        this.providerName = Objects.requireNonNull(providerName, "providerName is required");
        this.chatModel = Objects.requireNonNull(chatModel, "chatModel is required");
        this.optionsFactory = Objects.requireNonNull(optionsFactory, "optionsFactory is required");
        this.maxGenerationDuration = Objects.requireNonNull(
                maxGenerationDuration,
                "maxGenerationDuration is required"
        );
    }

    @Override
    public ProviderCapabilities capabilities() {
        return new ProviderCapabilities(providerName, true, true, null);
    }

    @Override
    public Flux<ChatChunk> stream(ChatGenerationRequest request) {
        Objects.requireNonNull(request, "request is required");
        return Flux.defer(() -> streamRequest(request))
                .onErrorMap(ProviderFailureMapper::map);
    }

    private Flux<ChatChunk> streamRequest(ChatGenerationRequest request) {
        Prompt prompt = new Prompt(toMessages(request.turns()), optionsFactory.apply(request));
        GenerationState state = new GenerationState();
        Mono<Void> deadline = Mono.delay(maxGenerationDuration)
                .flatMap(ignored -> Mono.error(new TimeoutException("AI provider generation deadline exceeded")));

        return chatModel.stream(prompt)
                .<ChatChunk>handle((response, sink) -> {
                    String delta = state.capture(response);
                    if (delta != null && !delta.isEmpty()) {
                        sink.next(ChatChunk.delta(delta));
                    }
                })
                .takeUntilOther(deadline)
                .concatWith(Mono.fromSupplier(state::terminalChunk));
    }

    private List<Message> toMessages(List<ChatTurn> turns) {
        return turns.stream().map(this::toMessage).toList();
    }

    private Message toMessage(ChatTurn turn) {
        return switch (turn.role()) {
            case SYSTEM -> new SystemMessage(turn.content());
            case USER -> new UserMessage(turn.content());
            case ASSISTANT -> new AssistantMessage(turn.content());
        };
    }

    private static final class GenerationState {

        private String finishReason;
        private Integer inputTokens;
        private Integer outputTokens;

        String capture(ChatResponse response) {
            if (response == null) {
                return null;
            }
            Generation generation = response.getResult();
            String delta = captureGeneration(generation);
            if (response.getMetadata() != null) {
                captureUsage(response.getMetadata().getUsage());
            }
            return delta;
        }

        ChatChunk terminalChunk() {
            return ChatChunk.terminal(finishReason, inputTokens, outputTokens);
        }

        private String captureGeneration(Generation generation) {
            if (generation == null) {
                return null;
            }
            if (generation.getMetadata() != null && generation.getMetadata().getFinishReason() != null) {
                finishReason = generation.getMetadata().getFinishReason();
            }
            return generation.getOutput() == null ? null : generation.getOutput().getText();
        }

        private void captureUsage(Usage usage) {
            if (usage == null) {
                return;
            }
            inputTokens = nonNegativeOrCurrent(usage.getPromptTokens(), inputTokens);
            outputTokens = nonNegativeOrCurrent(usage.getCompletionTokens(), outputTokens);
        }

        private Integer nonNegativeOrCurrent(Integer candidate, Integer current) {
            return candidate != null && candidate >= 0 ? candidate : current;
        }
    }
}
