package com.agricore.traceability;

import com.agricore.traceability.api.request.CreateTraceabilityRequest;
import com.agricore.traceability.api.response.PublicTraceabilityResponse;
import com.agricore.traceability.application.service.TraceabilityApplicationService;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import javax.imageio.ImageIO;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TraceabilityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private TraceabilityApplicationService traceabilityService;

    @Test
    void publicLookup_omitsSensitiveFields() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID harvestId = UUID.randomUUID();

        PublicTraceabilityResponse created = traceabilityService.createFromHarvest(
                new CreateTraceabilityRequest(
                        eventId,
                        harvestId,
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "Nong trai Dak Lak",
                        "DL-A01",
                        "Ca phe Robusta",
                        "TR4",
                        LocalDate.of(2025, 3, 1),
                        LocalDate.of(2026, 3, 15),
                        "GRADE_A",
                        new BigDecimal("3300"),
                        "Organic fertilizer, drip irrigation",
                        "COFFEE-ROBUSTA",
                        new BigDecimal("3500")
                )
        );

        JsonNode body = objectMapper.valueToTree(created);
        String code = created.traceabilityCode();
        String qrUrl = created.qrUrl();
        assertThat(created.farmName()).isEqualTo("Nong trai Dak Lak");
        assertThat(created.productCode()).isEqualTo("COFFEE-ROBUSTA");
        assertThat(created.grossWeightKg()).isEqualByComparingTo("3500");
        assertThat(created.traceabilityCode()).isNotBlank();
        assertThat(created.qrUrl()).isNotBlank();

        // No internal UUID fields in public response
        assertThat(body.has("harvestBatchId")).isFalse();
        assertThat(body.has("cropCycleId")).isFalse();
        assertThat(body.has("employeeId")).isFalse();
        assertThat(body.has("cost")).isFalse();
        assertThat(body.has("password")).isFalse();
        assertThat(body.get("qrImageUrl").asText()).isEqualTo(qrUrl + "/qr");

        mockMvc.perform(get("/public/api/v1/traceability/" + code))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productName").value("Ca phe Robusta"))
                .andExpect(jsonPath("$.qualityGrade").value("GRADE_A"));

        MvcResult qrImage = mockMvc.perform(get("/public/api/v1/traceability/" + code + "/qr"))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(result.getResponse().getContentType()).isEqualTo("image/png"))
                .andExpect(result -> assertThat(result.getResponse().getHeader("Cache-Control"))
                        .isEqualTo("public, max-age=86400, immutable"))
                .andReturn();
        BinaryBitmap qrBitmap = new BinaryBitmap(new HybridBinarizer(new BufferedImageLuminanceSource(
                ImageIO.read(new ByteArrayInputStream(qrImage.getResponse().getContentAsByteArray()))
        )));
        assertThat(new MultiFormatReader().decode(qrBitmap).getText()).isEqualTo(qrUrl);
    }
}
