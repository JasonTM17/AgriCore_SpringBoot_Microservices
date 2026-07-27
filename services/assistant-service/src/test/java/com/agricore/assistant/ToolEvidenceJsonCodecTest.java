package com.agricore.assistant;

import com.agricore.assistant.application.model.ToolEvidenceSnapshot;
import com.agricore.assistant.application.model.ToolFact;
import com.agricore.assistant.application.model.ToolSource;
import com.agricore.assistant.infrastructure.persistence.ToolEvidenceJsonCodec;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ToolEvidenceJsonCodecTest {

    private final ToolEvidenceJsonCodec codec = new ToolEvidenceJsonCodec(JsonMapper.builder().build());

    @Test
    void roundTripsCanonicalEvidenceWithoutAddingComputedProperties() {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("code", "FARM-01");
        fields.put("status", "ACTIVE");
        ToolEvidenceSnapshot snapshot = new ToolEvidenceSnapshot(List.of(
                new ToolFact("FARM-1", ToolSource.FARM, fields)
        ));

        String encoded = codec.encode(snapshot);

        assertThat(encoded).isEqualTo("""
                {"facts":[{"citationId":"FARM-1","source":"FARM","fields":{"code":"FARM-01","status":"ACTIVE"}}]}""");
        assertThat(codec.decode(encoded)).isEqualTo(snapshot);
        assertThat(codec.encode(ToolEvidenceSnapshot.empty())).isEqualTo("{\"facts\":[]}");
    }

    @Test
    void rejectsUnknownTrailingMalformedAndOversizedDocuments() {
        assertInvalid("{\"facts\":[],\"secret\":true}");
        assertInvalid("{\"facts\":[]} trailing");
        assertInvalid("null");
        assertInvalid("{\"facts\":null}");
        assertInvalid("{\"facts\":[{\"citationId\":\"FARM-1\",\"source\":null,\"fields\":{\"code\":\"A\"}}]}");
        assertInvalid(" ".repeat(24_001));
        assertInvalid("{\"facts\":[{\"citationId\":\"BAD ID\",\"source\":\"FARM\",\"fields\":{\"code\":\"A\"}}]}");
    }

    private void assertInvalid(String json) {
        assertThatThrownBy(() -> codec.decode(json))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("tool evidence JSON is invalid");
    }
}
