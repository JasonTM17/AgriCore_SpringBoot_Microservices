package com.agricore.harvest;

import com.agricore.farmaccess.FarmAccessClient;
import com.agricore.harvest.domain.model.HarvestStatus;
import com.agricore.harvest.infrastructure.persistence.HarvestBatchJpaRepository;
import com.agricore.harvest.infrastructure.persistence.OutboxJpaRepository;
import com.agricore.harvest.infrastructure.persistence.entity.HarvestBatchEntity;
import com.agricore.harvest.infrastructure.persistence.entity.OutboxEventEntity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class HarvestIntegrationTest {

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

    @BeforeEach
    void authorizePlots() {
        HarvestTestAccessSupport.authorizeAllPlots(farmAccessClient);
    }

    @Test
    void completeHarvest_persistsEventIdentityAcrossReload() throws Exception {
        String code = "HB-" + System.nanoTime();
        MvcResult created = mockMvc.perform(post("/api/v1/harvests/complete")
                        .header("X-Dev-User", "mgr")
                        .header("X-Dev-Roles", "FARM_MANAGER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code":"%s",
                                  "cropCycleId":"%s",
                                  "plotId":"%s",
                                  "warehouseId":"%s",
                                  "productCode":"COFFEE-ROBUSTA",
                                  "grossWeightKg":3500,
                                  "netWeightKg":3300,
                                  "qualityGrade":"GRADE_A",
                                  "farmName":"Nong trai Dak Lak",
                                  "plotCode":"DL-A01",
                                  "productName":"Ca phe Robusta",
                                  "careSummary":"Drip irrigation"
                                }
                                """.formatted(code, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.netWeightKg").value(3300))
                .andExpect(jsonPath("$.lastOutboxEventId").isNotEmpty())
                .andReturn();

        JsonNode createdBody = objectMapper.readTree(created.getResponse().getContentAsString());
        String harvestId = createdBody.get("id").asText();
        String eventId = createdBody.get("lastOutboxEventId").asText();
        OutboxEventEntity outboxEvent = outboxRepository.findAll().stream()
                .filter(event -> event.getPayload().contains("\"harvestId\":\"" + harvestId + "\""))
                .findFirst()
                .orElseThrow();

        assertThat(objectMapper.readTree(outboxEvent.getPayload()).get("eventId").asText())
                .isEqualTo(eventId);

        mockMvc.perform(get("/api/v1/harvests/{harvestId}", harvestId)
                        .header("X-Dev-User", "mgr")
                        .header("X-Dev-Roles", "FARM_MANAGER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lastOutboxEventId").value(eventId));
    }

    @Test
    void getHarvest_keepsLegacyEventIdentityNullable() throws Exception {
        Instant now = Instant.now();
        HarvestBatchEntity legacyBatch = new HarvestBatchEntity();
        legacyBatch.setId(UUID.randomUUID());
        legacyBatch.setCode("LEGACY-" + System.nanoTime());
        legacyBatch.setCropCycleId(UUID.randomUUID());
        legacyBatch.setPlotId(UUID.randomUUID());
        legacyBatch.setWarehouseId(UUID.randomUUID());
        legacyBatch.setProductCode("COFFEE-ROBUSTA");
        legacyBatch.setGrossWeightKg(new BigDecimal("3500.000"));
        legacyBatch.setNetWeightKg(new BigDecimal("3300.000"));
        legacyBatch.setQualityGrade("GRADE_A");
        legacyBatch.setStatus(HarvestStatus.COMPLETED);
        legacyBatch.setHarvestedAt(now);
        legacyBatch.setCreatedAt(now);
        legacyBatch.setUpdatedAt(now);
        harvestRepository.saveAndFlush(legacyBatch);

        mockMvc.perform(get("/api/v1/harvests/{harvestId}", legacyBatch.getId())
                        .header("X-Dev-User", "mgr")
                        .header("X-Dev-Roles", "FARM_MANAGER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lastOutboxEventId").value(nullValue()));
    }
}
