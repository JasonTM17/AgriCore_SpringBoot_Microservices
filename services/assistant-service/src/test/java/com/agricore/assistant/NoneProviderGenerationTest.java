package com.agricore.assistant;

import com.agricore.assistant.api.response.AssistantDtos.ConversationResponse;
import com.agricore.assistant.application.AssistantApplicationService;
import com.agricore.assistant.domain.AssistantException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verification: with provider=none the service boots, capabilities stay unavailable,
 * and generation returns structured PROVIDER_UNAVAILABLE (HTTP 503 semantics) — not a crash.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "agricore.assistant.provider=none",
        "agricore.assistant.openai-api-key="
})
class NoneProviderGenerationTest {

    @Autowired
    private AssistantApplicationService assistantService;

    @Test
    void capabilities_reportUnavailable_whenProviderNone() {
        var caps = assistantService.capabilities();
        assertThat(caps.provider()).isEqualTo("none");
        assertThat(caps.generationAvailable()).isFalse();
        assertThat(caps.reason()).isNotBlank();
    }

    @Test
    void startGeneration_whenProviderNone_throwsProviderUnavailable503() {
        UUID owner = UUID.randomUUID();
        ConversationResponse conversation = assistantService.createConversation(
                owner, List.of("FARM_MANAGER"), "No provider chat", null);

        assertThatThrownBy(() -> assistantService.startGeneration(
                owner,
                List.of("FARM_MANAGER"),
                conversation.id(),
                "Ping when no LLM is configured",
                "idem-none-1"
        ))
                .isInstanceOf(AssistantException.class)
                .satisfies(ex -> {
                    AssistantException ae = (AssistantException) ex;
                    assertThat(ae.getCode()).isEqualTo("PROVIDER_UNAVAILABLE");
                    assertThat(ae.getHttpStatus()).isEqualTo(503);
                    assertThat(ae.getMessage()).containsIgnoringCase("unavailable");
                });
    }
}
