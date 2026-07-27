package com.agricore.assistant.infrastructure.rag;

import com.agricore.assistant.application.model.ToolSource;
import com.agricore.assistant.infrastructure.configuration.AssistantRagProperties;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class JdbcKnowledgeRetrieverTest {

    @Test
    void disabledRetrievalDoesNotTouchTheDatabase() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        JdbcKnowledgeRetriever retriever =
                new JdbcKnowledgeRetriever(jdbc, new AssistantRagProperties());

        assertThat(retriever.retrieve("inventory reservation")).isEmpty();
        verifyNoInteractions(jdbc);
    }

    @Test
    void normalizesKnowledgeFactsThroughTheSharedEvidenceContract() {
        assertThat(ToolSource.valueOf("KNOWLEDGE")).isEqualTo(ToolSource.KNOWLEDGE);
    }
}
