package com.agricore.traceability;

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
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.ByteArrayInputStream;
import java.util.UUID;
import javax.imageio.ImageIO;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

    @Test
    void publicLookup_omitsSensitiveFields() throws Exception {
        String eventId = UUID.randomUUID().toString();
        UUID harvestId = UUID.randomUUID();

        MvcResult created = mockMvc.perform(post("/api/v1/traceability/batches")
                        .header("X-Dev-User", "system")
                        .header("X-Dev-Roles", "SYSTEM_ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "eventId":"%s",
                                  "harvestBatchId":"%s",
                                  "cropCycleId":"%s",
                                  "plotId":"%s",
                                  "farmName":"Nong trai Dak Lak",
                                  "plotCode":"DL-A01",
                                  "productName":"Ca phe Robusta",
                                  "varietyName":"TR4",
                                  "plantingDate":"2025-03-01",
                                  "harvestDate":"2026-03-15",
                                  "qualityGrade":"GRADE_A",
                                  "netWeightKg":3300,
                                  "careSummary":"Organic fertilizer, drip irrigation"
                                }
                                """.formatted(eventId, harvestId, UUID.randomUUID(), UUID.randomUUID())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.farmName").value("Nong trai Dak Lak"))
                .andExpect(jsonPath("$.traceabilityCode").isNotEmpty())
                .andExpect(jsonPath("$.qrUrl").isNotEmpty())
                .andReturn();

        JsonNode body = objectMapper.readTree(created.getResponse().getContentAsString());
        String code = body.get("traceabilityCode").asText();
        String qrUrl = body.get("qrUrl").asText();

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
