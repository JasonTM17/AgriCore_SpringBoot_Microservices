package com.agricore.sales.application.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SalesSagaFailureMessageTest {

    @Test
    void boundedCapsAndNormalizesStoredDiagnostics() {
        String result = SalesSagaFailureMessage.bounded(
                "safe prefix\r\n" + "x".repeat(1_000)
        );

        assertThat(result)
                .startsWith("safe prefix ")
                .doesNotContain("\r", "\n")
                .hasSize(SalesSagaFailureMessage.MAX_LENGTH);
    }

    @Test
    void publicMessageRedactsArbitraryHistoricalDiagnostics() {
        String storedMessage = "<html>password=super-secret</html>" + "x".repeat(1_000);

        assertThat(SalesSagaFailureMessage.publicMessage(storedMessage))
                .isEqualTo("Inventory saga failed")
                .doesNotContain("super-secret");
    }

    @Test
    void publicMessageReturnsOnlyAllowlistedStructuredFailure() {
        String storedMessage = "reservation outcome unknown: "
                + "Inventory request failed (status=503, code=INVENTORY_UNAVAILABLE)";

        assertThat(SalesSagaFailureMessage.publicMessage(storedMessage))
                .isEqualTo("Inventory request failed (status=503, code=INVENTORY_UNAVAILABLE)");
    }
}
