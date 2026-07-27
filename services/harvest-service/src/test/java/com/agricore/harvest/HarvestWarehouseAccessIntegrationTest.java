package com.agricore.harvest;

import com.agricore.farmaccess.FarmAccessClient;
import com.agricore.harvest.domain.exception.HarvestException;
import com.agricore.harvest.domain.model.HarvestStatus;
import com.agricore.harvest.infrastructure.client.WarehouseAccessClient;
import com.agricore.harvest.infrastructure.persistence.HarvestBatchJpaRepository;
import com.agricore.harvest.infrastructure.persistence.OutboxJpaRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class HarvestWarehouseAccessIntegrationTest {

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
    @MockitoBean
    private WarehouseAccessClient warehouseAccessClient;

    @BeforeEach
    void authorizePlots() {
        HarvestTestAccessSupport.authorizeAllPlots(farmAccessClient);
    }

    @Test
    void complete_acceptsWarehouseFromAuthoritativeFarm() throws Exception {
        UUID warehouseId = UUID.randomUUID();

        mockMvc.perform(completeRequest(
                        "WAREHOUSE-OK-" + System.nanoTime(),
                        UUID.randomUUID(),
                        warehouseId
                ))
                .andExpect(status().isCreated());

        verify(warehouseAccessClient)
                .requireWarehouse(warehouseId, HarvestTestAccessSupport.FARM_ID);
    }

    @ParameterizedTest
    @MethodSource("warehouseAccessFailures")
    void complete_rejectsWarehouseBeforeBatchAndOutboxCommit(
            HttpStatus status,
            String code
    ) throws Exception {
        UUID warehouseId = UUID.randomUUID();
        long batchesBefore = harvestRepository.count();
        long outboxBefore = outboxRepository.count();
        rejectWarehouse(warehouseId, status, code);

        mockMvc.perform(completeRequest(
                        "WAREHOUSE-REJECTED-" + System.nanoTime(),
                        UUID.randomUUID(),
                        warehouseId
                ))
                .andExpect(status().is(status.value()))
                .andExpect(jsonPath("$.code").value(code));

        assertThat(harvestRepository.count()).isEqualTo(batchesBefore);
        assertThat(outboxRepository.count()).isEqualTo(outboxBefore);
    }

    @Test
    void start_rejectsMissingWarehouseBeforeLifecycleCommit() throws Exception {
        UUID warehouseId = UUID.randomUUID();
        long batchesBefore = harvestRepository.count();
        long outboxBefore = outboxRepository.count();
        rejectWarehouse(
                warehouseId,
                HttpStatus.NOT_FOUND,
                "WAREHOUSE_NOT_FOUND"
        );

        mockMvc.perform(startRequest(UUID.randomUUID(), warehouseId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("WAREHOUSE_NOT_FOUND"));

        assertThat(harvestRepository.count()).isEqualTo(batchesBefore);
        assertThat(outboxRepository.count()).isEqualTo(outboxBefore);
    }

    @Test
    void completion_revalidatesPersistedWarehouseBeforeCompletionEventCommit() throws Exception {
        UUID warehouseId = UUID.randomUUID();
        MvcResult started = mockMvc.perform(startRequest(UUID.randomUUID(), warehouseId))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode startedBody = objectMapper.readTree(started.getResponse().getContentAsString());
        UUID harvestId = UUID.fromString(startedBody.path("id").asText());
        long outboxBefore = outboxRepository.count();
        rejectWarehouse(
                warehouseId,
                HttpStatus.NOT_FOUND,
                "WAREHOUSE_NOT_FOUND"
        );

        mockMvc.perform(authenticated(post(
                                "/api/v1/harvests/{harvestId}/complete",
                                harvestId
                        ))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "grossWeightKg":100.000,
                                  "netWeightKg":95.000,
                                  "qualityGrade":"GRADE_A"
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("WAREHOUSE_NOT_FOUND"));

        assertThat(outboxRepository.count()).isEqualTo(outboxBefore);
        assertThat(harvestRepository.findById(harvestId).orElseThrow().getStatus())
                .isEqualTo(HarvestStatus.IN_PROGRESS);
    }

    private void rejectWarehouse(UUID warehouseId, HttpStatus status, String code) {
        doThrow(new HarvestException(code, "Warehouse access rejected", status.value()))
                .when(warehouseAccessClient)
                .requireWarehouse(warehouseId, HarvestTestAccessSupport.FARM_ID);
    }

    private static MockHttpServletRequestBuilder completeRequest(
            String code,
            UUID plotId,
            UUID warehouseId
    ) {
        return authenticated(post("/api/v1/harvests/complete"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "code":"%s",
                          "cropCycleId":"%s",
                          "plotId":"%s",
                          "warehouseId":"%s",
                          "productCode":"COFFEE",
                          "grossWeightKg":100,
                          "netWeightKg":90,
                          "qualityGrade":"GRADE_A"
                        }
                        """.formatted(code, UUID.randomUUID(), plotId, warehouseId));
    }

    private static MockHttpServletRequestBuilder startRequest(
            UUID plotId,
            UUID warehouseId
    ) {
        return authenticated(post("/api/v1/harvests"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "code":"START-WAREHOUSE-%s",
                          "cropCycleId":"%s",
                          "plotId":"%s",
                          "warehouseId":"%s",
                          "productCode":"COFFEE"
                        }
                        """.formatted(
                        System.nanoTime(),
                        UUID.randomUUID(),
                        plotId,
                        warehouseId
                ));
    }

    private static MockHttpServletRequestBuilder authenticated(
            MockHttpServletRequestBuilder request
    ) {
        return request
                .header("X-Dev-User", "manager")
                .header("X-Dev-Roles", "FARM_MANAGER");
    }

    private static Stream<Arguments> warehouseAccessFailures() {
        return Stream.of(
                Arguments.of(HttpStatus.NOT_FOUND, "WAREHOUSE_NOT_FOUND"),
                Arguments.of(HttpStatus.SERVICE_UNAVAILABLE, "WAREHOUSE_ACCESS_UNAVAILABLE")
        );
    }
}
