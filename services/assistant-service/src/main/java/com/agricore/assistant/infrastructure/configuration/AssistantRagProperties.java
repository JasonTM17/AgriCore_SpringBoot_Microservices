package com.agricore.assistant.infrastructure.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties("agricore.assistant.rag")
public class AssistantRagProperties {

    private boolean enabled;
    private int maxResults = 4;
    private int maxQueryTerms = 12;
    private int maxExcerptCharacters = 220;
    private Duration queryTimeout = Duration.ofSeconds(2);

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getMaxResults() {
        return maxResults;
    }

    public void setMaxResults(int maxResults) {
        this.maxResults = maxResults;
    }

    public int getMaxQueryTerms() {
        return maxQueryTerms;
    }

    public void setMaxQueryTerms(int maxQueryTerms) {
        this.maxQueryTerms = maxQueryTerms;
    }

    public int getMaxExcerptCharacters() {
        return maxExcerptCharacters;
    }

    public void setMaxExcerptCharacters(int maxExcerptCharacters) {
        this.maxExcerptCharacters = maxExcerptCharacters;
    }

    public Duration getQueryTimeout() {
        return queryTimeout;
    }

    public void setQueryTimeout(Duration queryTimeout) {
        this.queryTimeout = queryTimeout;
    }

    public int validatedMaxResults() {
        if (maxResults < 1 || maxResults > 4) {
            throw invalid("max-results must be between 1 and 4");
        }
        return maxResults;
    }

    public int validatedMaxQueryTerms() {
        if (maxQueryTerms < 1 || maxQueryTerms > 20) {
            throw invalid("max-query-terms must be between 1 and 20");
        }
        return maxQueryTerms;
    }

    public int validatedMaxExcerptCharacters() {
        if (maxExcerptCharacters < 80 || maxExcerptCharacters > 240) {
            throw invalid("max-excerpt-characters must be between 80 and 240");
        }
        return maxExcerptCharacters;
    }

    public int validatedQueryTimeoutSeconds() {
        if (queryTimeout == null || queryTimeout.isZero() || queryTimeout.isNegative()
                || queryTimeout.compareTo(Duration.ofSeconds(10)) > 0) {
            throw invalid("query-timeout must be positive and at most 10 seconds");
        }
        return Math.toIntExact(Math.max(1, queryTimeout.toSeconds()));
    }

    private static IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException("Invalid assistant RAG configuration: " + message);
    }
}
