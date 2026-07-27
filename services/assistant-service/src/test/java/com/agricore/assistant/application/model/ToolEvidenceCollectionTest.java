package com.agricore.assistant.application.model;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ToolEvidenceCollectionTest {

    @Test
    void auditMetadataContainsOnlySchemaNamesAndOperationalMeasurements() {
        ToolEvidenceCollection collection = ToolEvidenceCollection.collected(
                new ToolEvidenceSnapshot(List.of(
                        new ToolFact("FARM-1", ToolSource.FARM, Map.of(
                                "name", "Private farm name",
                                "status", "ACTIVE"
                        ))
                )),
                17
        );

        assertThat(collection.auditMetadata()).isEqualTo("""
                {"outcome":"COLLECTED","latencyMs":17,"factCount":1,"sources":["FARM"],"egressFields":["name","status"]}""");
        assertThat(collection.auditMetadata()).doesNotContain("Private farm name", "ACTIVE", "FARM-1");
    }

    @Test
    void enforcesCoherentCollectedSkippedAndUnavailableStates() {
        assertThat(ToolEvidenceCollection.skipped("TOOLS_DISABLED").evidence()).isEqualTo(
                ToolEvidenceSnapshot.empty());
        assertThat(ToolEvidenceCollection.unavailable("TOOL_RESPONSE_INVALID", 5).outcome())
                .isEqualTo(ToolCollectionOutcome.UNAVAILABLE);
        assertThat(ToolEvidenceCollection.denied("TOOL_SCOPE_UNAVAILABLE", 3).outcome())
                .isEqualTo(ToolCollectionOutcome.DENIED);
        ToolEvidenceSnapshot evidence = new ToolEvidenceSnapshot(List.of(
                new ToolFact("FARM-1", ToolSource.FARM, Map.of("status", "ACTIVE"))
        ));
        ToolEvidenceCollection partial = ToolEvidenceCollection.partial(
                evidence, "RAG_DEPENDENCY_UNAVAILABLE", 7);
        assertThat(partial.outcome()).isEqualTo(ToolCollectionOutcome.PARTIAL);
        assertThat(partial.evidence()).isEqualTo(evidence);
        assertThat(partial.reasonCode()).isEqualTo("RAG_DEPENDENCY_UNAVAILABLE");
        assertThatThrownBy(() -> ToolEvidenceCollection.collected(ToolEvidenceSnapshot.empty(), 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ToolEvidenceCollection.partial(
                ToolEvidenceSnapshot.empty(), "RAG_DEPENDENCY_UNAVAILABLE", 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ToolEvidenceCollection(
                ToolEvidenceSnapshot.empty(), ToolCollectionOutcome.SKIPPED, "unsafe-reason", 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
