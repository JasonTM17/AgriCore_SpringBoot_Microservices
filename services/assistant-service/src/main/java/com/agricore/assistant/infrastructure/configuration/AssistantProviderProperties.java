package com.agricore.assistant.infrastructure.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.time.Duration;

@ConfigurationProperties(prefix = "agricore.assistant.provider")
public class AssistantProviderProperties {

    private static final int MIN_INPUT_CHARACTERS = 1_024;

    private ProviderType type = ProviderType.NONE;
    private String model = "";
    private String apiKey = "";
    private URI baseUrl;
    private Duration connectTimeout = Duration.ofSeconds(5);
    private Duration readTimeout = Duration.ofSeconds(60);
    private Duration maxGenerationDuration = Duration.ofSeconds(90);
    private Duration circuitOpenDuration = Duration.ofSeconds(30);
    private int maxInputCharacters = 40_000;
    private int maxOutputTokens = 1_024;
    private int circuitFailureThreshold = 5;
    private double temperature = 0.2;

    public ProviderType getType() {
        return type;
    }

    public void setType(ProviderType type) {
        this.type = type == null ? ProviderType.NONE : type;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        String normalized = model == null ? "" : model.strip();
        if (normalized.length() > 128) {
            throw new IllegalArgumentException("model must be at most 128 characters");
        }
        this.model = normalized;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey == null ? "" : apiKey.strip();
    }

    public URI getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(URI baseUrl) {
        if (baseUrl != null && !isSafeHttpUrl(baseUrl)) {
            throw new IllegalArgumentException(
                    "baseUrl must be an absolute HTTP(S) URL without credentials, query, or fragment"
            );
        }
        this.baseUrl = baseUrl;
    }

    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(Duration connectTimeout) {
        this.connectTimeout = requireDurationAtMost(connectTimeout, Duration.ofSeconds(30), "connectTimeout");
    }

    public Duration getReadTimeout() {
        return readTimeout;
    }

    public void setReadTimeout(Duration readTimeout) {
        this.readTimeout = requireDurationAtMost(readTimeout, Duration.ofMinutes(5), "readTimeout");
    }

    public Duration getMaxGenerationDuration() {
        return maxGenerationDuration;
    }

    public void setMaxGenerationDuration(Duration maxGenerationDuration) {
        this.maxGenerationDuration = requireDurationAtMost(
                maxGenerationDuration,
                Duration.ofMinutes(10),
                "maxGenerationDuration"
        );
    }

    public Duration getCircuitOpenDuration() {
        return circuitOpenDuration;
    }

    public void setCircuitOpenDuration(Duration circuitOpenDuration) {
        this.circuitOpenDuration = requireDurationAtMost(
                circuitOpenDuration,
                Duration.ofMinutes(10),
                "circuitOpenDuration"
        );
    }

    public int getMaxInputCharacters() {
        return maxInputCharacters;
    }

    public void setMaxInputCharacters(int maxInputCharacters) {
        this.maxInputCharacters = requireRange(
                maxInputCharacters,
                MIN_INPUT_CHARACTERS,
                200_000,
                "maxInputCharacters"
        );
    }

    public int getMaxOutputTokens() {
        return maxOutputTokens;
    }

    public void setMaxOutputTokens(int maxOutputTokens) {
        this.maxOutputTokens = requireRange(maxOutputTokens, 1, 8_192, "maxOutputTokens");
    }

    public int getCircuitFailureThreshold() {
        return circuitFailureThreshold;
    }

    public void setCircuitFailureThreshold(int circuitFailureThreshold) {
        this.circuitFailureThreshold = requireRange(circuitFailureThreshold, 1, 100, "circuitFailureThreshold");
    }

    public double getTemperature() {
        return temperature;
    }

    public void setTemperature(double temperature) {
        if (!Double.isFinite(temperature) || temperature < 0 || temperature > 2) {
            throw new IllegalArgumentException("temperature must be between 0 and 2");
        }
        this.temperature = temperature;
    }

    private static boolean isSafeHttpUrl(URI uri) {
        String scheme = uri.getScheme();
        return uri.isAbsolute()
                && uri.getHost() != null
                && uri.getUserInfo() == null
                && uri.getQuery() == null
                && uri.getFragment() == null
                && ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme));
    }

    private static Duration requireDurationAtMost(Duration value, Duration maximum, String fieldName) {
        if (value == null || value.isZero() || value.isNegative() || value.compareTo(maximum) > 0) {
            throw new IllegalArgumentException(fieldName + " must be positive and at most " + maximum);
        }
        return value;
    }

    private static int requireRange(int value, int minimum, int maximum, String fieldName) {
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException(fieldName + " must be between " + minimum + " and " + maximum);
        }
        return value;
    }

    public enum ProviderType {
        NONE("none"),
        OPENAI("openai"),
        OLLAMA("ollama");

        private final String externalName;

        ProviderType(String externalName) {
            this.externalName = externalName;
        }

        public String externalName() {
            return externalName;
        }
    }
}
