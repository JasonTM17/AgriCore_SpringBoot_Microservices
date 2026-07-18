package com.agricore.traceability;

import com.agricore.traceability.infrastructure.persistence.ProcessedEventJpaRepository;
import com.agricore.traceability.infrastructure.persistence.TraceabilityBatchJpaRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
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

    private static Stream<Arguments> invalidFields() {
        return Stream.of(
                Arguments.of("eventId", "E".repeat(101)),
                Arguments.of("farmName", "F".repeat(201)),
                Arguments.of("plotCode", "P".repeat(65)),
                Arguments.of("productName", "N".repeat(201)),
                Arguments.of("varietyName", "V".repeat(201)),
                Arguments.of("qualityGrade", "Q".repeat(33)),
                Arguments.of("netWeightKg", new BigDecimal("100000000000")),
                Arguments.of("netWeightKg", new BigDecimal("1.0001"))
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
