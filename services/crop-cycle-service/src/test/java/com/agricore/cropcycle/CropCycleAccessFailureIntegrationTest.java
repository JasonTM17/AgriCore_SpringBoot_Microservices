package com.agricore.cropcycle;

import com.agricore.cropcycle.domain.model.CycleStage;
import com.agricore.cropcycle.infrastructure.persistence.CropCycleJpaRepository;
import com.agricore.cropcycle.infrastructure.persistence.OutboxJpaRepository;
import com.agricore.farmaccess.FarmAccessClient;
import com.agricore.farmaccess.FarmAccessException;
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
class CropCycleAccessFailureIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private CropCycleJpaRepository cycleRepository;
    @Autowired
    private OutboxJpaRepository outboxRepository;
    @MockitoBean
    private FarmAccessClient farmAccessClient;

    @ParameterizedTest
    @MethodSource("farmAccessFailures")
    void create_whenFarmAccessFails_writesNeitherCycleNorOutbox(
            HttpStatus status,
            String code
    ) throws Exception {
        UUID farmId = UUID.randomUUID();
        UUID plotId = UUID.randomUUID();
        long cyclesBefore = cycleRepository.count();
        long outboxBefore = outboxRepository.count();
        doThrow(farmError(status, code)).when(farmAccessClient).requireFarmPlot(farmId, plotId);

        assertApiError(
                mockMvc.perform(post("/api/v1/crop-cycles")
                        .header("X-Dev-User", "agronomist")
                        .header("X-Dev-Roles", "AGRONOMIST")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequest("DENIED-" + System.nanoTime(), farmId, plotId))),
                status,
                code
        );

        assertThat(cycleRepository.count()).isEqualTo(cyclesBefore);
        assertThat(outboxRepository.count()).isEqualTo(outboxBefore);
    }

    @Test
    void create_whenRoleIsInsufficient_returnsStructuredErrorBeforeFarmLookup() throws Exception {
        assertApiError(
                mockMvc.perform(post("/api/v1/crop-cycles")
                        .header("X-Dev-User", "worker")
                        .header("X-Dev-Roles", "FIELD_WORKER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequest(
                                "ROLE-" + System.nanoTime(),
                                UUID.randomUUID(),
                                UUID.randomUUID()
                        ))),
                HttpStatus.FORBIDDEN,
                "ACCESS_DENIED"
        );

        verifyNoInteractions(farmAccessClient);
    }

    @Test
    void get_whenFarmAccessIsMaskedNotFound_doesNotLeakCycleData() throws Exception {
        String cycleCode = "GET-" + System.nanoTime();
        UUID farmId = UUID.randomUUID();
        UUID plotId = UUID.randomUUID();
        UUID cycleId = createAccepted(cycleCode, farmId, plotId);
        doThrow(farmError(HttpStatus.NOT_FOUND, "FARM_RESOURCE_NOT_FOUND"))
                .when(farmAccessClient).requireFarmPlot(farmId, plotId);

        String body = assertApiError(
                mockMvc.perform(get("/api/v1/crop-cycles/{cycleId}", cycleId)
                        .header("X-Dev-User", "worker")
                        .header("X-Dev-Roles", "FIELD_WORKER")),
                HttpStatus.NOT_FOUND,
                "FARM_RESOURCE_NOT_FOUND"
        ).andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain(cycleCode);
    }

    @Test
    void changeStage_whenFarmAccessIsDenied_preservesCycleAndOutbox() throws Exception {
        UUID farmId = UUID.randomUUID();
        UUID plotId = UUID.randomUUID();
        UUID cycleId = createAccepted("STAGE-" + System.nanoTime(), farmId, plotId);
        long outboxBefore = outboxRepository.count();
        doThrow(farmError(HttpStatus.FORBIDDEN, "FARM_ACCESS_DENIED"))
                .when(farmAccessClient).requireFarmPlot(farmId, plotId);

        assertApiError(
                mockMvc.perform(post("/api/v1/crop-cycles/{cycleId}/stage", cycleId)
                        .header("X-Dev-User", "agronomist")
                        .header("X-Dev-Roles", "AGRONOMIST")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"stage\":\"LAND_PREPARATION\"}")),
                HttpStatus.FORBIDDEN,
                "FARM_ACCESS_DENIED"
        );

        assertThat(cycleRepository.findById(cycleId).orElseThrow().getStage()).isEqualTo(CycleStage.PLANNED);
        assertThat(outboxRepository.count()).isEqualTo(outboxBefore);
    }

    private UUID createAccepted(String code, UUID farmId, UUID plotId) throws Exception {
        String body = mockMvc.perform(post("/api/v1/crop-cycles")
                        .header("X-Dev-User", "agronomist")
                        .header("X-Dev-Roles", "AGRONOMIST")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequest(code, farmId, plotId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(body).get("id").asText());
    }

    private static String createRequest(String code, UUID farmId, UUID plotId) {
        return """
                {"code":"%s","farmId":"%s","plotId":"%s","cropId":"%s",
                 "plannedStartDate":"2026-03-01","plannedEndDate":"2026-11-30"}
                """.formatted(code, farmId, plotId, UUID.randomUUID());
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

    private static Stream<Arguments> farmAccessFailures() {
        return Stream.of(
                Arguments.of(HttpStatus.FORBIDDEN, "FARM_ACCESS_DENIED"),
                Arguments.of(HttpStatus.NOT_FOUND, "FARM_RESOURCE_NOT_FOUND"),
                Arguments.of(HttpStatus.SERVICE_UNAVAILABLE, "FARM_ACCESS_UNAVAILABLE")
        );
    }
}
