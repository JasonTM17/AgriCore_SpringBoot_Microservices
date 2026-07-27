package com.agricore.assistant.infrastructure.configuration;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AssistantRagPropertiesTest {

    @Test
    void keepsRetrievalDisabledWithBoundedDefaults() {
        AssistantRagProperties properties = new AssistantRagProperties();

        assertThat(properties.isEnabled()).isFalse();
        assertThat(properties.validatedMaxResults()).isEqualTo(4);
        assertThat(properties.validatedMaxQueryTerms()).isEqualTo(12);
        assertThat(properties.validatedMaxExcerptCharacters()).isEqualTo(220);
        assertThat(properties.validatedQueryTimeoutSeconds()).isEqualTo(2);
    }

    @Test
    void rejectsUnboundedRetrievalSettings() {
        AssistantRagProperties properties = new AssistantRagProperties();

        properties.setMaxResults(5);
        assertThatThrownBy(properties::validatedMaxResults)
                .isInstanceOf(IllegalArgumentException.class);

        properties.setMaxResults(4);
        properties.setMaxQueryTerms(21);
        assertThatThrownBy(properties::validatedMaxQueryTerms)
                .isInstanceOf(IllegalArgumentException.class);

        properties.setMaxQueryTerms(12);
        properties.setMaxExcerptCharacters(241);
        assertThatThrownBy(properties::validatedMaxExcerptCharacters)
                .isInstanceOf(IllegalArgumentException.class);

        properties.setMaxExcerptCharacters(220);
        properties.setQueryTimeout(Duration.ofSeconds(11));
        assertThatThrownBy(properties::validatedQueryTimeoutSeconds)
                .isInstanceOf(IllegalArgumentException.class);
    }
}
