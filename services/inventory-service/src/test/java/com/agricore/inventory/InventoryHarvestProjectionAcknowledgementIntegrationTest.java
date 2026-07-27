package com.agricore.inventory;

import com.agricore.farmaccess.FarmAccessClient;
import com.agricore.farmaccess.FarmAccessException;
import com.agricore.inventory.application.service.InventoryApplicationService;
import com.agricore.inventory.infrastructure.persistence.ProcessedEventJpaRepository;
import com.agricore.inventory.infrastructure.persistence.WarehouseJpaRepository;
import com.agricore.inventory.infrastructure.persistence.entity.ProcessedEventEntity;
import com.agricore.inventory.infrastructure.persistence.entity.WarehouseEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Instant;
import java.util.UUID;

import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
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
    @Autowired
    private WarehouseJpaRepository warehouseRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @MockitoBean
    private FarmAccessClient farmAccessClient;

    @Test
    void acknowledgement_reportsConsumerLedgerState() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID farmId = UUID.randomUUID();
        UUID warehouseId = createWarehouse(farmId);

        mockMvc.perform(acknowledgementRequest(eventId, warehouseId, "FARM_MANAGER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventId").value(eventId.toString()))
                .andExpect(jsonPath("$.projection").value("INVENTORY"))
                .andExpect(jsonPath("$.state").value("NOT_ACKNOWLEDGED"))
                .andExpect(jsonPath("$.acknowledgedAt").value(nullValue()));

        processedEventRepository.saveAndFlush(ProcessedEventEntity.of(
                eventId.toString(),
                InventoryApplicationService.HARVEST_CONSUMER,
                farmId,
                warehouseId
        ));

        mockMvc.perform(acknowledgementRequest(eventId, warehouseId, "FARM_MANAGER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("ACKNOWLEDGED"))
                .andExpect(jsonPath("$.acknowledgedAt").value(notNullValue()));
        verify(farmAccessClient, times(2)).requireFarm(farmId);
    }

    @Test
    void acknowledgement_doesNotRevealAnEventOwnedByAnotherWarehouse() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID callerFarmId = UUID.randomUUID();
        UUID callerWarehouseId = createWarehouse(callerFarmId);
        UUID otherFarmId = UUID.randomUUID();
        UUID otherWarehouseId = createWarehouse(otherFarmId);
        processedEventRepository.saveAndFlush(ProcessedEventEntity.of(
                eventId.toString(),
                InventoryApplicationService.HARVEST_CONSUMER,
                otherFarmId,
                otherWarehouseId
        ));

        mockMvc.perform(acknowledgementRequest(eventId, callerWarehouseId, "FARM_MANAGER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("NOT_ACKNOWLEDGED"))
                .andExpect(jsonPath("$.acknowledgedAt").value(nullValue()));
        verify(farmAccessClient).requireFarm(callerFarmId);
    }

    @Test
    void acknowledgement_rejectsCallerWithoutWarehouseFarmAccess() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID farmId = UUID.randomUUID();
        UUID warehouseId = createWarehouse(farmId);
        doThrow(new FarmAccessException("FARM_ACCESS_DENIED", "Farm access denied", 403))
                .when(farmAccessClient)
                .requireFarm(farmId);

        mockMvc.perform(acknowledgementRequest(eventId, warehouseId, "FARM_MANAGER"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FARM_ACCESS_DENIED"));
    }

    @Test
    void acknowledgement_failsClosedWhenFarmAccessIsUnavailable() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID farmId = UUID.randomUUID();
        UUID warehouseId = createWarehouse(farmId);
        doThrow(new FarmAccessException(
                "FARM_ACCESS_UNAVAILABLE",
                "Farm access verification is temporarily unavailable",
                503
        )).when(farmAccessClient).requireFarm(farmId);

        mockMvc.perform(acknowledgementRequest(eventId, warehouseId, "FARM_MANAGER"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("FARM_ACCESS_UNAVAILABLE"));
    }

    @Test
    void acknowledgement_failsClosedForLegacyMarkersWithoutTenantScope() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID farmId = UUID.randomUUID();
        UUID warehouseId = createWarehouse(farmId);
        jdbcTemplate.update(
                """
                INSERT INTO processed_events (event_id, consumer_name, processed_at)
                VALUES (?, ?, CURRENT_TIMESTAMP)
                """,
                eventId.toString(),
                InventoryApplicationService.HARVEST_CONSUMER
        );

        mockMvc.perform(acknowledgementRequest(eventId, warehouseId, "FARM_MANAGER"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("ACKNOWLEDGEMENT_SCOPE_UNAVAILABLE"));
        verify(farmAccessClient).requireFarm(farmId);
    }

    @Test
    void acknowledgement_enforcesHarvestWorkflowRoles() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();

        mockMvc.perform(get(acknowledgementPath(eventId)).param("warehouseId", warehouseId.toString()))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(acknowledgementRequest(eventId, warehouseId, "SALES_STAFF"))
                .andExpect(status().isForbidden());
    }

    private static MockHttpServletRequestBuilder acknowledgementRequest(
            UUID eventId,
            UUID warehouseId,
            String roles
    ) {
        return get(acknowledgementPath(eventId))
                .param("warehouseId", warehouseId.toString())
                .header("X-Dev-User", "harvest-user")
                .header("X-Dev-Roles", roles);
    }

    private static String acknowledgementPath(UUID eventId) {
        return "/api/v1/inventory/events/harvest-completed/" + eventId + "/acknowledgement";
    }

    private UUID createWarehouse(UUID farmId) {
        WarehouseEntity warehouse = new WarehouseEntity();
        warehouse.setId(UUID.randomUUID());
        warehouse.setFarmId(farmId);
        warehouse.setCode("ACK-" + UUID.randomUUID());
        warehouse.setName("Acknowledgement test warehouse");
        warehouse.setCreatedAt(Instant.now());
        return warehouseRepository.saveAndFlush(warehouse).getId();
    }
}
