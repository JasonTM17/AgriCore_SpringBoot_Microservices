package com.agricore.harvest;

import com.agricore.farmaccess.FarmAccessClient;
import com.agricore.farmaccess.FarmAccessException;
import com.agricore.harvest.infrastructure.persistence.HarvestBatchJpaRepository;
import com.agricore.harvest.infrastructure.persistence.OutboxJpaRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class HarvestAccessFailureIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private HarvestBatchJpaRepository harvestRepository;
    @Autowired
    private OutboxJpaRepository outboxRepository;
    @MockitoBean
    private FarmAccessClient farmAccessClient;

    @ParameterizedTest
    @MethodSource("farmAccessFailures")
    void complete_whenFarmAccessFails_writesNeitherBatchNorOutbox(
            HttpStatus status,
            String code
    ) throws Exception {
        UUID plotId = UUID.randomUUID();
        long batchesBefore = harvestRepository.count();
        long outboxBefore = outboxRepository.count();
        doThrow(farmError(status, code)).when(farmAccessClient).requirePlot(plotId);

        assertApiError(
                mockMvc.perform(post("/api/v1/harvests/complete")
                        .header("X-Dev-User", "manager")
                        .header("X-Dev-Roles", "FARM_MANAGER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest("DENIED-" + System.nanoTime(), plotId))),
                status,
                code
        );

        assertThat(harvestRepository.count()).isEqualTo(batchesBefore);
        assertThat(outboxRepository.count()).isEqualTo(outboxBefore);
    }

    @Test
    void get_whenFarmAccessIsMaskedNotFound_doesNotLeakHarvestData() throws Exception {
        String harvestCode = "GET-" + System.nanoTime();
        UUID plotId = UUID.randomUUID();
        UUID harvestId = createAccepted(harvestCode, plotId);
        doThrow(farmError(HttpStatus.NOT_FOUND, "FARM_RESOURCE_NOT_FOUND"))
                .when(farmAccessClient).requirePlot(plotId);

        String body = assertApiError(
                mockMvc.perform(get("/api/v1/harvests/{harvestId}", harvestId)
                        .header("X-Dev-User", "worker")
                        .header("X-Dev-Roles", "FIELD_WORKER")),
                HttpStatus.NOT_FOUND,
                "HARVEST_NOT_FOUND"
        ).andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain(harvestCode);
    }

    @Test
    void completionEventStatus_whenFarmAccessIsMaskedNotFound_doesNotLeakEventData() throws Exception {
        UUID plotId = UUID.randomUUID();
        UUID harvestId = createAccepted("STATUS-" + System.nanoTime(), plotId);
        String eventId = harvestRepository.findById(harvestId)
                .orElseThrow()
                .getLastOutboxEventId()
                .toString();
        doThrow(farmError(HttpStatus.NOT_FOUND, "FARM_RESOURCE_NOT_FOUND"))
                .when(farmAccessClient).requirePlot(plotId);

        String body = assertApiError(
                mockMvc.perform(get("/api/v1/harvests/{harvestId}/completion-event", harvestId)
                        .header("X-Dev-User", "worker")
                        .header("X-Dev-Roles", "FIELD_WORKER")),
                HttpStatus.NOT_FOUND,
                "HARVEST_NOT_FOUND"
        ).andReturn().getResponse().getContentAsString();
        String missingBody = assertApiError(
                mockMvc.perform(get("/api/v1/harvests/{harvestId}/completion-event", UUID.randomUUID())
                        .header("X-Dev-User", "worker")
                        .header("X-Dev-Roles", "FIELD_WORKER")),
                HttpStatus.NOT_FOUND,
                "HARVEST_NOT_FOUND"
        ).andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain(eventId);
        assertThat(stableError(body)).isEqualTo(stableError(missingBody));
    }

    @Test
    void complete_whenRoleIsInsufficient_returnsStructuredErrorBeforeFarmLookup() throws Exception {
        assertApiError(
                mockMvc.perform(post("/api/v1/harvests/complete")
                        .header("X-Dev-User", "worker")
                        .header("X-Dev-Roles", "FIELD_WORKER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest("ROLE-" + System.nanoTime(), UUID.randomUUID()))),
                HttpStatus.FORBIDDEN,
                "ACCESS_DENIED"
        );

        verifyNoInteractions(farmAccessClient);
    }

    private UUID createAccepted(String code, UUID plotId) throws Exception {
        String body = mockMvc.perform(post("/api/v1/harvests/complete")
                        .header("X-Dev-User", "manager")
                        .header("X-Dev-Roles", "FARM_MANAGER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest(code, plotId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(body).get("id").asText());
    }

    private static String validRequest(String code, UUID plotId) {
        return """
                {"code":"%s","cropCycleId":"%s","plotId":"%s","warehouseId":"%s",
                 "productCode":"COFFEE","grossWeightKg":100,"netWeightKg":90,"qualityGrade":"GRADE_A"}
                """.formatted(code, UUID.randomUUID(), plotId, UUID.randomUUID());
    }

    private static ResultActions assertApiError(
            ResultActions result,
            HttpStatus expectedStatus,
            String expectedCode
    ) throws Exception {
        return result.andExpect(status().is(expectedStatus.value()))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.timestamp").isString())
                .andExpect(jsonPath("$.status").value(expectedStatus.value()))
                .andExpect(jsonPath("$.error").value(expectedStatus.getReasonPhrase()))
                .andExpect(jsonPath("$.code").value(expectedCode))
                .andExpect(jsonPath("$.message").isString())
                .andExpect(jsonPath("$.path").isString());
    }

    private static FarmAccessException farmError(HttpStatus status, String code) {
        return new FarmAccessException(code, "Farm access rejected", status.value());
    }

    private JsonNode stableError(String body) throws Exception {
        com.fasterxml.jackson.databind.node.ObjectNode error =
                (com.fasterxml.jackson.databind.node.ObjectNode) objectMapper.readTree(body);
        error.remove("timestamp");
        error.remove("path");
        return error;
    }

    private static Stream<Arguments> farmAccessFailures() {
        return Stream.of(
                Arguments.of(HttpStatus.FORBIDDEN, "FARM_ACCESS_DENIED"),
                Arguments.of(HttpStatus.NOT_FOUND, "FARM_RESOURCE_NOT_FOUND"),
                Arguments.of(HttpStatus.SERVICE_UNAVAILABLE, "FARM_ACCESS_UNAVAILABLE")
        );
    }
}
