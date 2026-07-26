package com.agricore.traceability;

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

import java.util.UUID;

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

        // No internal UUID fields in public response
        assertThat(body.has("harvestBatchId")).isFalse();
        assertThat(body.has("cropCycleId")).isFalse();
        assertThat(body.has("employeeId")).isFalse();
        assertThat(body.has("cost")).isFalse();
        assertThat(body.has("password")).isFalse();

        mockMvc.perform(get("/public/api/v1/traceability/" + code))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productName").value("Ca phe Robusta"))
                .andExpect(jsonPath("$.qualityGrade").value("GRADE_A"));
    }

    /**
     * The QR lookup is the one endpoint on the platform an end consumer reaches directly, so a
     * miss must answer in the documented {@code ApiError} shape rather than Boot's default error
     * body, which carries neither a code nor a message.
     */
    @Test
    void unknownPublicCode_returnsPlatformErrorContract() throws Exception {
        mockMvc.perform(get("/public/api/v1/traceability/NOSUCH-CODE"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Traceability code not found"))
                .andExpect(jsonPath("$.path").value("/public/api/v1/traceability/NOSUCH-CODE"));
    }

    /**
     * A redelivery whose event id was already consumed short-circuits to the stored projection.
     * When the projection is missing for the batch the event names, the service reports CONFLICT —
     * not NOT_FOUND. The advice must forward the status the caller threw instead of flattening
     * every {@code ResponseStatusException} to 404.
     */
    @Test
    void replayedEventWithMissingBatch_keepsConflictStatus() throws Exception {
        String eventId = UUID.randomUUID().toString();

        mockMvc.perform(postBatch(eventId, UUID.randomUUID()))
                .andExpect(status().isCreated());

        // Same event id, different batch: the idempotency guard fires, the lookup finds nothing.
        mockMvc.perform(postBatch(eventId, UUID.randomUUID()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.code").value("CONFLICT"))
                .andExpect(jsonPath("$.message").value("Event processed but batch missing"));
    }

    /**
     * The traceability code is derived from the product name and the harvest id, and nothing checks
     * it against the table before inserting. A harvest republished under a fresh event id therefore
     * regenerates the identical code and collides with {@code uk_traceability_code}.
     *
     * <p>That used to answer 500 — "the server is broken, retry" — for a request that can never
     * succeed. The distinction matters most on the Kafka path, where the listener wraps the failure
     * and the batch lands in the DLT as an opaque server error rather than a recognisable duplicate.
     */
    @Test
    void republishedHarvestUnderANewEventId_reportsDuplicateNotServerError() throws Exception {
        UUID sameHarvestBatch = UUID.randomUUID();

        mockMvc.perform(postBatch(UUID.randomUUID().toString(), sameHarvestBatch))
                .andExpect(status().isCreated());

        mockMvc.perform(postBatch(UUID.randomUUID().toString(), sameHarvestBatch))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.code").value("DUPLICATE_RESOURCE"));
    }

    /**
     * The constraint name identifies the index and so the schema. It belongs in the log, where an
     * operator can act on it, not in a body served to whoever made the request.
     */
    @Test
    void duplicateResponse_doesNotNameTheConstraint() throws Exception {
        UUID sameHarvestBatch = UUID.randomUUID();
        mockMvc.perform(postBatch(UUID.randomUUID().toString(), sameHarvestBatch))
                .andExpect(status().isCreated());

        String body = mockMvc.perform(postBatch(UUID.randomUUID().toString(), sameHarvestBatch))
                .andExpect(status().isConflict())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(body)
                .doesNotContain("uk_traceability_code")
                .doesNotContain("traceability_batches")
                .doesNotContain("23505");
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder postBatch(
            String eventId,
            UUID harvestBatchId
    ) {
        return post("/api/v1/traceability/batches")
                .header("X-Dev-User", "system")
                .header("X-Dev-Roles", "SYSTEM_ADMIN")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "eventId":"%s",
                          "harvestBatchId":"%s",
                          "cropCycleId":"%s",
                          "plotId":"%s",
                          "farmName":"Nong trai Lam Dong",
                          "plotCode":"LD-B02",
                          "productName":"Tra Oolong",
                          "varietyName":"Kim Tuyen",
                          "plantingDate":"2025-04-01",
                          "harvestDate":"2026-04-20",
                          "qualityGrade":"GRADE_A",
                          "netWeightKg":1200,
                          "careSummary":"Shade grown"
                        }
                        """.formatted(eventId, harvestBatchId, UUID.randomUUID(), UUID.randomUUID()));
    }
}
