package com.agricore.assistant.infrastructure.provider;

import com.agricore.assistant.application.model.ChatGenerationRequest;
import com.agricore.assistant.application.port.ChatProvider;
import com.agricore.assistant.infrastructure.configuration.AssistantProviderProperties;
import com.agricore.assistant.infrastructure.configuration.AssistantProviderProperties.ProviderType;
import io.micrometer.observation.ObservationRegistry;
import io.netty.channel.ChannelOption;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import reactor.netty.http.client.HttpClient;

import java.net.URI;
import java.util.Objects;

public final class SpringAiChatProviderFactory {

    private static final URI DEFAULT_OPENAI_BASE_URL = URI.create("https://api.openai.com");
    private static final URI DEFAULT_OLLAMA_BASE_URL = URI.create("http://localhost:11434");

    private final ObservationRegistry observationRegistry;

    public SpringAiChatProviderFactory(ObservationRegistry observationRegistry) {
        this.observationRegistry = Objects.requireNonNull(observationRegistry, "observationRegistry is required");
    }

    public ChatProvider create(AssistantProviderProperties properties) {
        Objects.requireNonNull(properties, "properties are required");
        return switch (properties.getType()) {
            case NONE -> new UnavailableChatProvider(ProviderType.NONE);
            case OPENAI -> createOpenAi(properties);
            case OLLAMA -> createOllama(properties);
        };
    }

    private ChatProvider createOpenAi(AssistantProviderProperties properties) {
        if (properties.getModel().isBlank() || properties.getApiKey().isBlank()) {
            return UnavailableChatProvider.misconfigured(ProviderType.OPENAI);
        }
        ProviderHttpClients clients = httpClients(properties);
        OpenAiApi api = OpenAiApi.builder()
                .baseUrl(baseUrl(properties, DEFAULT_OPENAI_BASE_URL).toString())
                .apiKey(properties.getApiKey())
                .restClientBuilder(clients.restClient())
                .webClientBuilder(clients.webClient())
                .build();
        OpenAiChatModel model = OpenAiChatModel.builder()
                .openAiApi(api)
                .defaultOptions(openAiOptions(properties))
                .retryTemplate(singleAttemptRetry())
                .observationRegistry(observationRegistry)
                .build();
        return withCircuitBreaker(new SpringAiChatProvider(
                ProviderType.OPENAI.externalName(),
                model,
                SpringAiChatProviderFactory::openAiOptions,
                properties.getMaxGenerationDuration()
        ), properties);
    }

    private ChatProvider createOllama(AssistantProviderProperties properties) {
        if (properties.getModel().isBlank()) {
            return UnavailableChatProvider.misconfigured(ProviderType.OLLAMA);
        }
        ProviderHttpClients clients = httpClients(properties);
        OllamaApi api = OllamaApi.builder()
                .baseUrl(baseUrl(properties, DEFAULT_OLLAMA_BASE_URL).toString())
                .restClientBuilder(clients.restClient())
                .webClientBuilder(clients.webClient())
                .build();
        OllamaChatModel model = OllamaChatModel.builder()
                .ollamaApi(api)
                .defaultOptions(ollamaOptions(properties))
                .retryTemplate(singleAttemptRetry())
                .observationRegistry(observationRegistry)
                .build();
        return withCircuitBreaker(new SpringAiChatProvider(
                ProviderType.OLLAMA.externalName(),
                model,
                SpringAiChatProviderFactory::ollamaOptions,
                properties.getMaxGenerationDuration()
        ), properties);
    }

    private ChatProvider withCircuitBreaker(
            ChatProvider provider,
            AssistantProviderProperties properties
    ) {
        return new CircuitBreakingChatProvider(
                provider,
                new ProviderCircuitBreaker(
                        properties.getCircuitFailureThreshold(),
                        properties.getCircuitOpenDuration()
                )
        );
    }

    private ProviderHttpClients httpClients(AssistantProviderProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.getConnectTimeout());
        requestFactory.setReadTimeout(properties.getReadTimeout());

        HttpClient httpClient = HttpClient.create()
                .option(
                        ChannelOption.CONNECT_TIMEOUT_MILLIS,
                        Math.toIntExact(properties.getConnectTimeout().toMillis())
                )
                .responseTimeout(properties.getReadTimeout());
        return new ProviderHttpClients(
                RestClient.builder().requestFactory(requestFactory),
                WebClient.builder().clientConnector(new ReactorClientHttpConnector(httpClient))
        );
    }

    private URI baseUrl(AssistantProviderProperties properties, URI defaultValue) {
        return properties.getBaseUrl() == null ? defaultValue : properties.getBaseUrl();
    }

    private RetryTemplate singleAttemptRetry() {
        return RetryTemplate.builder().maxAttempts(1).noBackoff().build();
    }

    private static OpenAiChatOptions openAiOptions(AssistantProviderProperties properties) {
        return OpenAiChatOptions.builder()
                .model(properties.getModel())
                .maxTokens(properties.getMaxOutputTokens())
                .temperature(properties.getTemperature())
                .N(1)
                .streamUsage(true)
                .build();
    }

    private static OpenAiChatOptions openAiOptions(ChatGenerationRequest request) {
        return OpenAiChatOptions.builder()
                .model(request.model())
                .maxTokens(request.maxOutputTokens())
                .temperature(request.temperature())
                .N(1)
                .streamUsage(true)
                .build();
    }

    private static OllamaChatOptions ollamaOptions(AssistantProviderProperties properties) {
        return OllamaChatOptions.builder()
                .model(properties.getModel())
                .numPredict(properties.getMaxOutputTokens())
                .temperature(properties.getTemperature())
                .disableThinking()
                .build();
    }

    private static OllamaChatOptions ollamaOptions(ChatGenerationRequest request) {
        return OllamaChatOptions.builder()
                .model(request.model())
                .numPredict(request.maxOutputTokens())
                .temperature(request.temperature())
                .disableThinking()
                .build();
    }

    private record ProviderHttpClients(RestClient.Builder restClient, WebClient.Builder webClient) {
    }
}
