package com.agricore.assistant.infrastructure.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "agricore.assistant.budget")
public class AssistantBudgetProperties {

    private boolean enabled = true;
    private Duration window = Duration.ofMinutes(1);
    private int maxRequests = 20;
    private int maxTokens = 20_000;
    private String keyPrefix = "rl:assistant";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Duration getWindow() {
        return window;
    }

    public void setWindow(Duration window) {
        if (window == null || window.compareTo(Duration.ofSeconds(1)) < 0
                || window.compareTo(Duration.ofHours(1)) > 0) {
            throw new IllegalArgumentException("budget window must be between one second and one hour");
        }
        this.window = window;
    }

    public int getMaxRequests() {
        return maxRequests;
    }

    public void setMaxRequests(int maxRequests) {
        if (maxRequests < 1 || maxRequests > 100_000) {
            throw new IllegalArgumentException("budget max requests must be between 1 and 100000");
        }
        this.maxRequests = maxRequests;
    }

    public int getMaxTokens() {
        return maxTokens;
    }

    public void setMaxTokens(int maxTokens) {
        if (maxTokens < 1 || maxTokens > 1_000_000) {
            throw new IllegalArgumentException("budget max tokens must be between 1 and 1000000");
        }
        this.maxTokens = maxTokens;
    }

    public String getKeyPrefix() {
        return keyPrefix;
    }

    public void setKeyPrefix(String keyPrefix) {
        String normalized = keyPrefix == null ? "" : keyPrefix.strip();
        if (!normalized.matches("[a-z][a-z0-9:-]{2,63}")) {
            throw new IllegalArgumentException("budget key prefix has an invalid format");
        }
        this.keyPrefix = normalized;
    }
}
