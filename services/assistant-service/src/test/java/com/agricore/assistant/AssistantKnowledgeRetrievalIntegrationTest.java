package com.agricore.assistant;

import com.agricore.assistant.application.model.ToolSource;
import com.agricore.assistant.application.port.KnowledgeRetriever;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "agricore.assistant.rag.enabled=true",
        "agricore.assistant.rag.max-results=4"
})
@ActiveProfiles("test")
class AssistantKnowledgeRetrievalIntegrationTest {

    @Autowired
    private KnowledgeRetriever retriever;

    @Test
    void ranksVietnameseInventoryKnowledgeAndReturnsBoundedCitations() {
        var facts = retriever.retrieve(
                "Làm thế nào để reservation tồn kho không bị âm?");

        assertThat(facts).isNotEmpty().hasSizeLessThanOrEqualTo(4);
        assertThat(facts.getFirst().citationId()).isEqualTo("KB-1");
        assertThat(facts.getFirst().source()).isEqualTo(ToolSource.KNOWLEDGE);
        assertThat(facts.getFirst().fields())
                .containsEntry("title", "An toàn đặt giữ tồn kho")
                .containsEntry("sourceUri", "docs/diagrams/inventory-reservation-saga.md");
        assertThat(facts.getFirst().fields().get("excerpt")).hasSizeLessThanOrEqualTo(220);
    }

    @Test
    void normalizesVietnameseDWithStrokeForIndexedTerms() {
        var facts = retriever.retrieve("đất");

        assertThat(facts).isNotEmpty();
        assertThat(facts.getFirst().fields())
                .containsEntry("title", "Quản lý nông trại và lô đất");
    }

    @Test
    void returnsNoEvidenceForUnrelatedTerms() {
        assertThat(retriever.retrieve("quantum zeppelin xylophone")).isEmpty();
    }

    @Test
    void bindsPromptTextInsteadOfTreatingItAsSql() {
        var facts = retriever.retrieve("inventory') OR ('1'='1");

        assertThat(facts).isNotEmpty().hasSizeLessThanOrEqualTo(4);
        assertThat(facts).allMatch(fact -> fact.citationId().matches("KB-[1-4]"));
    }
}
