package com.agricore.assistant.infrastructure.tool.farm;

import com.agricore.assistant.application.port.ToolCollectionException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FarmToolEvidenceProjectorTest {

    private static final Instant NOW = Instant.parse("2026-07-21T08:00:00Z");

    private final FarmToolEvidenceProjector projector = new FarmToolEvidenceProjector();

    @Test
    void rejectsSemanticBlankTextAndExponentExpansionBeforeProjection() {
        UUID farmId = UUID.randomUUID();

        assertInvalid(farmId, farm(farmId, "\u200B", BigDecimal.ONE));
        assertInvalid(farmId, farm(farmId, "Farm", new BigDecimal("1e+100000000")));
    }

    private void assertInvalid(UUID farmId, FarmToolResponseDecoder.FarmDetails farm) {
        assertThatThrownBy(() -> projector.project(farmId, farm, emptyPlots(), 20))
                .isInstanceOf(ToolCollectionException.class)
                .extracting("reasonCode")
                .isEqualTo("TOOL_RESPONSE_INVALID");
    }

    private FarmToolResponseDecoder.FarmDetails farm(
            UUID farmId,
            String name,
            BigDecimal totalArea
    ) {
        return new FarmToolResponseDecoder.FarmDetails(
                farmId, "FARM-1", name, null, null, totalArea, null, null,
                "ACTIVE", NOW, NOW, 1L
        );
    }

    private FarmToolResponseDecoder.PlotPage emptyPlots() {
        return new FarmToolResponseDecoder.PlotPage(
                List.of(), 0, 20, 0L, 0, true, true
        );
    }
}
