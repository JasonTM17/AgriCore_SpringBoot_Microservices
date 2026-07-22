package com.agricore.traceability.api.response;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Public QR payload — no internal IDs, staff PII, costs, or secrets.
 */
public record PublicTraceabilityResponse(
        String traceabilityCode,
        String productName,
        String varietyName,
        String farmName,
        String plotCode,
        LocalDate plantingDate,
        LocalDate harvestDate,
        String qualityGrade,
        BigDecimal netWeightKg,
        String careSummary,
        String qrUrl,
        String qrImageUrl,
        String batchLabel
) {
}
