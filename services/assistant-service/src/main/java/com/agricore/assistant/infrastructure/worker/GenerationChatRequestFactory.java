package com.agricore.assistant.infrastructure.worker;

import com.agricore.assistant.application.model.ChatGenerationRequest;
import com.agricore.assistant.application.model.ChatTurn;
import com.agricore.assistant.application.model.ChatTurnRole;
import com.agricore.assistant.application.model.GenerationExecutionContext;
import com.agricore.assistant.application.port.ChatGenerationPolicy;
import com.agricore.assistant.application.port.ToolEvidencePromptFormatter;
import com.agricore.assistant.domain.model.AssistantMessage;
import com.agricore.assistant.domain.model.MessageRole;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Component
public class GenerationChatRequestFactory {

    private static final int MAX_PROVIDER_TURNS = 100;

    private final ChatGenerationPolicy policy;
    private final ToolEvidencePromptFormatter evidenceRenderer;

    public GenerationChatRequestFactory(
            ChatGenerationPolicy policy,
            ToolEvidencePromptFormatter evidenceRenderer
    ) {
        this.policy = policy;
        this.evidenceRenderer = evidenceRenderer;
    }

    public ChatGenerationRequest create(GenerationExecutionContext context) {
        List<AssistantMessage> messages = context.messages();
        if (messages.isEmpty() || messages.getLast().role() != MessageRole.USER) {
            throw GenerationProcessingException.failed("GENERATION_CONTEXT_INVALID");
        }

        String systemPolicy = evidenceRenderer.systemPolicy();
        String evidencePrompt = evidenceRenderer.renderEvidence(context.generation().toolEvidence());
        int reservedTurns = evidencePrompt.isEmpty() ? 1 : 2;
        int historyBudget = policy.maxInputCharacters()
                - systemPolicy.length()
                - evidencePrompt.length();
        if (historyBudget < 1) {
            throw GenerationProcessingException.failed("AI_INPUT_TOO_LARGE");
        }
        List<AssistantMessage> selected = selectNewestMessages(
                messages,
                historyBudget,
                MAX_PROVIDER_TURNS - reservedTurns
        );
        List<ChatTurn> turns = new ArrayList<>();
        turns.add(new ChatTurn(ChatTurnRole.SYSTEM, systemPolicy));
        if (!evidencePrompt.isEmpty()) {
            turns.add(new ChatTurn(ChatTurnRole.USER, evidencePrompt));
        }
        turns.addAll(selected.stream()
                .map(message -> new ChatTurn(toChatRole(message.role()), message.content()))
                .toList());
        String model = context.generation().model();
        if (model == null || model.isBlank()) {
            throw GenerationProcessingException.failed("GENERATION_MODEL_INVALID");
        }
        return new ChatGenerationRequest(
                turns,
                model,
                policy.maxOutputTokens(),
                policy.temperature()
        );
    }

    private static List<AssistantMessage> selectNewestMessages(
            List<AssistantMessage> messages,
            int maximumCharacters,
            int maximumMessages
    ) {
        List<AssistantMessage> selected = new ArrayList<>();
        int remaining = maximumCharacters;
        for (int index = messages.size() - 1; index >= 0; index--) {
            if (selected.size() == maximumMessages) {
                break;
            }
            AssistantMessage message = messages.get(index);
            String content = message.content();
            if (content == null || content.isBlank()) {
                throw GenerationProcessingException.failed("GENERATION_CONTEXT_INVALID");
            }
            if (content.length() > remaining) {
                if (selected.isEmpty()) {
                    throw GenerationProcessingException.failed("AI_INPUT_TOO_LARGE");
                }
                break;
            }
            selected.add(message);
            remaining -= content.length();
        }
        Collections.reverse(selected);
        return selected;
    }

    private static ChatTurnRole toChatRole(MessageRole role) {
        return switch (role) {
            case USER -> ChatTurnRole.USER;
            case ASSISTANT -> ChatTurnRole.ASSISTANT;
        };
    }
}
