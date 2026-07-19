package com.agricore.harvest;

import com.agricore.farmaccess.FarmAccessClient;
import com.agricore.farmaccess.FarmAccessException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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

import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class HarvestCompletionEventRepairAccessIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @MockitoBean
    private FarmAccessClient farmAccessClient;

    @Test
    void republish_masksInaccessibleHarvestLikeAMissingHarvest() throws Exception {
        CreatedHarvest harvest = completeHarvest();
        doThrow(farmError(HttpStatus.NOT_FOUND, "FARM_RESOURCE_NOT_FOUND"))
                .when(farmAccessClient).requirePlot(harvest.plotId());

        String inaccessibleBody = performRepair(harvest.harvestId(), HttpStatus.NOT_FOUND, "HARVEST_NOT_FOUND");
        String missingBody = performRepair(UUID.randomUUID(), HttpStatus.NOT_FOUND, "HARVEST_NOT_FOUND");

        assertThat(inaccessibleBody).doesNotContain(harvest.eventId().toString());
        assertThat(stableError(inaccessibleBody)).isEqualTo(stableError(missingBody));
    }

    @ParameterizedTest
    @MethodSource("visibleFarmAccessFailures")
    void republish_propagatesVisibleFarmAccessFailures(HttpStatus expectedStatus, String expectedCode)
            throws Exception {
        CreatedHarvest harvest = completeHarvest();
        doThrow(farmError(expectedStatus, expectedCode))
                .when(farmAccessClient).requirePlot(harvest.plotId());

        String body = performRepair(harvest.harvestId(), expectedStatus, expectedCode);

        assertThat(body).doesNotContain(harvest.eventId().toString());
    }

    private CreatedHarvest completeHarvest() throws Exception {
        UUID plotId = UUID.randomUUID();
        String response = mockMvc.perform(post("/api/v1/harvests/complete")
                        .header("X-Dev-User", "manager")
                        .header("X-Dev-Roles", "FARM_MANAGER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"REPAIR-ACCESS-%s","cropCycleId":"%s","plotId":"%s","warehouseId":"%s",
                                 "productCode":"COFFEE","grossWeightKg":100,"netWeightKg":90,"qualityGrade":"GRADE_A"}
                                """.formatted(
                                System.nanoTime(),
                                UUID.randomUUID(),
                                plotId,
                                UUID.randomUUID()
                        )))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode body = objectMapper.readTree(response);
        return new CreatedHarvest(
                UUID.fromString(body.get("id").asText()),
                plotId,
                UUID.fromString(body.get("lastOutboxEventId").asText())
        );
    }

    private String performRepair(UUID harvestId, HttpStatus expectedStatus, String expectedCode) throws Exception {
        return mockMvc.perform(post("/api/v1/harvests/{harvestId}/completion-event/republish", harvestId)
                        .header("X-Dev-User", "manager")
                        .header("X-Dev-Roles", "FARM_MANAGER"))
                .andExpect(status().is(expectedStatus.value()))
                .andExpect(jsonPath("$.code").value(expectedCode))
                .andReturn().getResponse().getContentAsString();
    }

    private ObjectNode stableError(String body) throws Exception {
        ObjectNode error = (ObjectNode) objectMapper.readTree(body);
        error.remove("timestamp");
        error.remove("path");
        return error;
    }

    private static FarmAccessException farmError(HttpStatus status, String code) {
        return new FarmAccessException(code, "Farm access rejected", status.value());
    }

    private static Stream<Arguments> visibleFarmAccessFailures() {
        return Stream.of(
                Arguments.of(HttpStatus.FORBIDDEN, "FARM_ACCESS_DENIED"),
                Arguments.of(HttpStatus.SERVICE_UNAVAILABLE, "FARM_ACCESS_UNAVAILABLE")
        );
    }

    private record CreatedHarvest(UUID harvestId, UUID plotId, UUID eventId) {
    }
}
