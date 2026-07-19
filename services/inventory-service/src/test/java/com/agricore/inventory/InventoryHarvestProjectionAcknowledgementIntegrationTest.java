package com.agricore.inventory;

import com.agricore.inventory.application.service.InventoryApplicationService;
import com.agricore.inventory.infrastructure.persistence.ProcessedEventJpaRepository;
import com.agricore.inventory.infrastructure.persistence.entity.ProcessedEventEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.UUID;

import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InventoryHarvestProjectionAcknowledgementIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ProcessedEventJpaRepository processedEventRepository;

    @Test
    void acknowledgement_reportsConsumerLedgerState() throws Exception {
        UUID eventId = UUID.randomUUID();

        mockMvc.perform(acknowledgementRequest(eventId, "FARM_MANAGER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventId").value(eventId.toString()))
                .andExpect(jsonPath("$.projection").value("INVENTORY"))
                .andExpect(jsonPath("$.state").value("NOT_ACKNOWLEDGED"))
                .andExpect(jsonPath("$.acknowledgedAt").value(nullValue()));

        processedEventRepository.saveAndFlush(ProcessedEventEntity.of(
                eventId.toString(),
                InventoryApplicationService.HARVEST_CONSUMER
        ));

        mockMvc.perform(acknowledgementRequest(eventId, "FARM_MANAGER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("ACKNOWLEDGED"))
                .andExpect(jsonPath("$.acknowledgedAt").value(notNullValue()));
    }

    @Test
    void acknowledgement_enforcesHarvestWorkflowRoles() throws Exception {
        UUID eventId = UUID.randomUUID();

        mockMvc.perform(get(acknowledgementPath(eventId)))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(acknowledgementRequest(eventId, "SALES_STAFF"))
                .andExpect(status().isForbidden());
    }

    private static MockHttpServletRequestBuilder acknowledgementRequest(
            UUID eventId,
            String roles
    ) {
        return get(acknowledgementPath(eventId))
                .header("X-Dev-User", "harvest-user")
                .header("X-Dev-Roles", roles);
    }

    private static String acknowledgementPath(UUID eventId) {
        return "/api/v1/inventory/events/harvest-completed/" + eventId + "/acknowledgement";
    }
}
