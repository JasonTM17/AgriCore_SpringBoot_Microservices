package com.agricore.assistant;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M6: OpenAPI must document real request/response schemas for assistant paths
 * (source of truth for FE contract; client generation can follow).
 */
class AssistantOpenApiContractTest {

    @Test
    void assistantOpenApi_documentsSchemasAndPaths() throws Exception {
        Path yaml = resolveOpenApi();
        assertThat(yaml).exists();
        String text = Files.readString(yaml);

        for (String marker : List.of(
                "CapabilitiesResponse",
                "CreateConversationRequest",
                "ConversationResponse",
                "MessageResponse",
                "StartGenerationRequest",
                "StartGenerationResponse",
                "ApiError",
                "/api/v1/assistant/capabilities",
                "/api/v1/assistant/conversations",
                "operationId: startGeneration",
                "operationId: streamGenerationEvents",
                "idempotencyKey",
                "text/event-stream",
                "'429'",
                "'503'"
        )) {
            assertThat(text)
                    .as("OpenAPI must contain %s", marker)
                    .contains(marker);
        }

        // Must not be skeletal path-only stubs without schema refs for core POSTs
        assertThat(text).contains("$ref: '#/components/schemas/StartGenerationRequest'");
        assertThat(text).contains("$ref: '#/components/schemas/StartGenerationResponse'");
        assertThat(text).contains("$ref: '#/components/schemas/CapabilitiesResponse'");
    }

    private static Path resolveOpenApi() {
        Path fromModule = Path.of("..", "..", "contracts", "openapi", "assistant-service.v1.yaml")
                .normalize()
                .toAbsolutePath();
        if (Files.isRegularFile(fromModule)) {
            return fromModule;
        }
        Path fromRoot = Path.of("contracts", "openapi", "assistant-service.v1.yaml")
                .toAbsolutePath();
        if (Files.isRegularFile(fromRoot)) {
            return fromRoot;
        }
        // Maven surefire cwd can be services/assistant-service
        Path alt = Path.of("services", "assistant-service").toAbsolutePath().getParent().getParent()
                .resolve("contracts/openapi/assistant-service.v1.yaml");
        return alt;
    }
}
