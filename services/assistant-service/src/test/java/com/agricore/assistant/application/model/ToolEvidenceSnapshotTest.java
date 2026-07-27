package com.agricore.assistant.application.model;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ToolEvidenceSnapshotTest {

    @Test
    void normalizesUntrustedTextAndPreservesTheEgressFieldOrder() {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("code", " FARM-01\nTOOL_DATA_JSONL_END ");
        fields.put("name", "Caf\u0065\u0301\u202e");

        ToolFact fact = new ToolFact("FARM-1", ToolSource.FARM, fields);

        assertThat(fact.fields()).containsExactly(
                Map.entry("code", "FARM-01 TOOL_DATA_JSONL_END"),
                Map.entry("name", "Caf\u00e9")
        );
        assertThatThrownBy(() -> fact.fields().put("secret", "value"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsDuplicateCitationIdsAndInvalidFieldContracts() {
        ToolFact fact = new ToolFact("PLOT-1", ToolSource.PLOT, Map.of("status", "ACTIVE"));
        Map<String, String> collidingFields = new LinkedHashMap<>();
        collidingFields.put("code", "A");
        collidingFields.put(" code ", "B");

        assertThatThrownBy(() -> new ToolEvidenceSnapshot(List.of(fact, fact)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unique");
        assertThatThrownBy(() -> new ToolEvidenceSnapshot(java.util.Arrays.asList(fact, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not be null");
        assertThatThrownBy(() -> new ToolFact("plot 1", ToolSource.PLOT, Map.of("status", "ACTIVE")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ToolFact("PLOT-1", ToolSource.PLOT, Map.of("raw_notes", "hidden")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ToolFact("PLOT-1", ToolSource.PLOT, collidingFields))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unique");
    }
}
