package com.agricore.assistant.infrastructure.provider;

import com.agricore.assistant.application.model.ChatChunk;
import com.agricore.assistant.application.model.ChatGenerationRequest;
import com.agricore.assistant.application.model.ChatTurn;
import com.agricore.assistant.application.model.ChatTurnRole;
import com.agricore.assistant.application.port.ChatProvider;
import com.agricore.assistant.infrastructure.configuration.AssistantProviderProperties;
import com.sun.net.httpserver.HttpServer;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class SpringAiProviderHttpIntegrationTest {

    private final AtomicReference<String> requestBody = new AtomicReference<>();
    private final AtomicReference<String> authorization = new AtomicReference<>();
    private final AtomicReference<String> responseBody = new AtomicReference<>();
    private final AtomicReference<String> responseContentType = new AtomicReference<>();
    private HttpServer server;
    private ExecutorService executor;

    @BeforeEach
    void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] response = responseBody.get().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", responseContentType.get());
            exchange.sendResponseHeaders(200, 0);
            try (var output = exchange.getResponseBody()) {
                output.write(response);
                output.flush();
            }
        });
        executor = Executors.newSingleThreadExecutor();
        server.setExecutor(executor);
        server.start();
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    @Test
    void streamsOpenAiSseAndKeepsCredentialOutOfRequestBody() {
        responseContentType.set("text/event-stream");
        responseBody.set("""
                data: {"id":"chatcmpl-test","object":"chat.completion.chunk","created":1,"model":"test-model","choices":[{"index":0,"delta":{"role":"assistant","content":"Xin "},"finish_reason":null}]}

                data: {"id":"chatcmpl-test","object":"chat.completion.chunk","created":1,"model":"test-model","choices":[{"index":0,"delta":{"content":"chào"},"finish_reason":null}]}

                data: {"id":"chatcmpl-test","object":"chat.completion.chunk","created":1,"model":"test-model","choices":[{"index":0,"delta":{},"finish_reason":"stop"}],"usage":{"prompt_tokens":7,"completion_tokens":2,"total_tokens":9}}

                data: [DONE]

                """);

        ChatProvider provider = provider(AssistantProviderProperties.ProviderType.OPENAI, "test-key");
        List<ChatChunk> chunks = provider.stream(request()).collectList().block(Duration.ofSeconds(5));

        assertThat(chunks).containsExactly(
                ChatChunk.delta("Xin "),
                ChatChunk.delta("chào"),
                ChatChunk.terminal("stop", 7, 2)
        );
        assertThat(authorization.get()).isEqualTo("Bearer test-key");
        assertThat(requestBody.get())
                .contains("\"model\":\"test-model\"")
                .contains("\"stream\":true")
                .contains("Xin chào")
                .doesNotContain("test-key");
    }

    @Test
    void streamsOllamaNdjsonAndDisablesThinking() {
        responseContentType.set("application/x-ndjson");
        responseBody.set("""
                {"model":"test-model","created_at":"2024-01-01T00:00:00Z","message":{"role":"assistant","content":"Xin "},"done":false}
                {"model":"test-model","created_at":"2024-01-01T00:00:00Z","message":{"role":"assistant","content":"chào"},"done":false}
                {"model":"test-model","created_at":"2024-01-01T00:00:00Z","message":{"role":"assistant","content":""},"done":true,"done_reason":"stop","prompt_eval_count":7,"eval_count":2}
                """);

        ChatProvider provider = provider(AssistantProviderProperties.ProviderType.OLLAMA, "");
        List<ChatChunk> chunks = provider.stream(request()).collectList().block(Duration.ofSeconds(5));

        assertThat(chunks).containsExactly(
                ChatChunk.delta("Xin "),
                ChatChunk.delta("chào"),
                ChatChunk.terminal("stop", 7, 2)
        );
        assertThat(authorization.get()).isNull();
        assertThat(requestBody.get())
                .contains("\"model\":\"test-model\"")
                .contains("\"stream\":true")
                .contains("\"think\":false")
                .doesNotContain("Authorization");
    }

    private ChatProvider provider(AssistantProviderProperties.ProviderType type, String apiKey) {
        AssistantProviderProperties properties = new AssistantProviderProperties();
        properties.setType(type);
        properties.setModel("test-model");
        properties.setApiKey(apiKey);
        properties.setBaseUrl(URI.create("http://localhost:" + server.getAddress().getPort()));
        properties.setConnectTimeout(Duration.ofSeconds(2));
        properties.setReadTimeout(Duration.ofSeconds(2));
        properties.setMaxGenerationDuration(Duration.ofSeconds(4));
        return new SpringAiChatProviderFactory(ObservationRegistry.NOOP).create(properties);
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
}
