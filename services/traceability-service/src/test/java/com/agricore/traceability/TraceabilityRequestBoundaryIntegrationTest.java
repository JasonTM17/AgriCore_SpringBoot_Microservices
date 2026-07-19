package com.agricore.traceability;

import com.agricore.traceability.api.request.CreateTraceabilityRequest;
import com.agricore.traceability.application.service.TraceabilityApplicationService;
import com.agricore.traceability.infrastructure.persistence.ProcessedEventJpaRepository;
import com.agricore.traceability.infrastructure.persistence.TraceabilityBatchJpaRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TraceabilityRequestBoundaryIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private TraceabilityBatchJpaRepository batchRepository;
    @Autowired
    private ProcessedEventJpaRepository processedEventRepository;
    @Autowired
    private TraceabilityApplicationService traceabilityService;

    @BeforeEach
    void clearPersistence() {
        processedEventRepository.deleteAll();
        batchRepository.deleteAll();
    }

    @ParameterizedTest(name = "rejects {0} outside persistence boundary")
    @MethodSource("invalidFields")
    void invalidStorageBoundValue_isRejectedBeforePersistence(String field, Object value) throws Exception {
        Map<String, Object> payload = validPayload();
        payload.put(field, value);

        mockMvc.perform(post("/api/v1/traceability/batches")
                        .header("X-Dev-User", "warehouse")
                        .header("X-Dev-Roles", "WAREHOUSE_MANAGER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isBadRequest());

        assertThat(batchRepository.count()).isZero();
        assertThat(processedEventRepository.count()).isZero();
    }

    @Test
    void oversizedPublicCode_isRejectedAtTheHttpBoundary() throws Exception {
        mockMvc.perform(get("/public/api/v1/traceability/{traceabilityCode}", "C".repeat(65)))
                .andExpect(status().isBadRequest());

        assertThat(batchRepository.count()).isZero();
    }

    @Test
    void maximumLengthPublicCode_reachesTheLookup() throws Exception {
        mockMvc.perform(get("/public/api/v1/traceability/{traceabilityCode}", "C".repeat(64)))
                .andExpect(status().isNotFound());
    }

    @Test
    void inclusiveStorageBoundValues_areAccepted() throws Exception {
        Map<String, Object> payload = validPayload();
        payload.put("netWeightKg", new BigDecimal("0.001"));
        payload.put("careSummary", "C".repeat(1000));

        mockMvc.perform(post("/api/v1/traceability/batches")
                        .header("X-Dev-User", "warehouse")
                        .header("X-Dev-Roles", "WAREHOUSE_MANAGER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated());

        assertThat(batchRepository.count()).isOne();
        assertThat(processedEventRepository.count()).isOne();
    }

    @Test
    void replayedEvent_returnsTheExistingProjectionWithoutCreatingAnotherBatch() throws Exception {
        String payload = objectMapper.writeValueAsString(validPayload());

        mockMvc.perform(post("/api/v1/traceability/batches")
                        .header("X-Dev-User", "warehouse")
                        .header("X-Dev-Roles", "WAREHOUSE_MANAGER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/traceability/batches")
                        .header("X-Dev-User", "warehouse")
                        .header("X-Dev-Roles", "WAREHOUSE_MANAGER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated());

        assertThat(batchRepository.count()).isOne();
        assertThat(processedEventRepository.count()).isOne();
    }

    @Test
    void nonHttpAdapter_cannotBypassProjectionValidation() {
        CreateTraceabilityRequest request = new CreateTraceabilityRequest(
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                null,
                "Farm",
                "PLOT-1",
                "Coffee",
                "TR4",
                null,
                LocalDate.of(2026, 3, 15),
                "GRADE_A",
                BigDecimal.ZERO,
                "C".repeat(1001)
        );

        assertThatThrownBy(() -> traceabilityService.createFromHarvest(request))
                .isInstanceOf(ConstraintViolationException.class);
        assertThat(batchRepository.count()).isZero();
        assertThat(processedEventRepository.count()).isZero();
    }

    private static Stream<Arguments> invalidFields() {
        return Stream.of(
                Arguments.of("eventId", "E".repeat(101)),
                Arguments.of("farmName", "F".repeat(201)),
                Arguments.of("plotCode", "P".repeat(65)),
                Arguments.of("productName", "N".repeat(201)),
                Arguments.of("varietyName", "V".repeat(201)),
                Arguments.of("qualityGrade", "Q".repeat(33)),
                Arguments.of("netWeightKg", new BigDecimal("100000000000")),
                Arguments.of("netWeightKg", new BigDecimal("1.0001")),
                Arguments.of("netWeightKg", BigDecimal.ZERO),
                Arguments.of("netWeightKg", new BigDecimal("-0.001")),
                Arguments.of("careSummary", "C".repeat(1001))
        );
    }

    private static Map<String, Object> validPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventId", UUID.randomUUID().toString());
        payload.put("harvestBatchId", UUID.randomUUID());
        payload.put("farmName", "Farm");
        payload.put("plotCode", "PLOT-1");
        payload.put("productName", "Coffee");
        payload.put("varietyName", "TR4");
        payload.put("harvestDate", "2026-03-15");
        payload.put("qualityGrade", "GRADE_A");
        payload.put("netWeightKg", new BigDecimal("12.345"));
        return payload;
    }
}
