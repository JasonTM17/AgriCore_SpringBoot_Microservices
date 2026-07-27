package com.agricore.inventory;

import com.agricore.inventory.infrastructure.persistence.WarehouseJpaRepository;
import com.agricore.inventory.infrastructure.persistence.entity.WarehouseEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InventoryWarehouseScopeIntegrationTest {

    private static final String INTERNAL_TOKEN =
            "test-inventory-work-service-token-012345678901234567890123";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private WarehouseJpaRepository warehouseRepository;

    @Test
    void scope_returnsAuthoritativeWarehouseFarm() throws Exception {
        UUID farmId = UUID.randomUUID();
        UUID warehouseId = createWarehouse(farmId);

        mockMvc.perform(get(scopePath(warehouseId))
                        .header("X-Internal-Service-Token", INTERNAL_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.warehouseId").value(warehouseId.toString()))
                .andExpect(jsonPath("$.farmId").value(farmId.toString()));
    }

    @Test
    void scope_returnsNotFoundForMissingWarehouse() throws Exception {
        mockMvc.perform(get(scopePath(UUID.randomUUID()))
                        .header("X-Internal-Service-Token", INTERNAL_TOKEN))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("WAREHOUSE_NOT_FOUND"));
    }

    @Test
    void scope_failsClosedForWarehouseWithoutFarmScope() throws Exception {
        UUID warehouseId = createWarehouse(null);

        mockMvc.perform(get(scopePath(warehouseId))
                        .header("X-Internal-Service-Token", INTERNAL_TOKEN))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("WAREHOUSE_SCOPE_UNAVAILABLE"));
    }

    @Test
    void scope_rejectsMissingOrInvalidInternalToken() throws Exception {
        UUID warehouseId = createWarehouse(UUID.randomUUID());

        mockMvc.perform(get(scopePath(warehouseId)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(scopePath(warehouseId))
                        .header("X-Internal-Service-Token", "invalid-token"))
                .andExpect(status().isForbidden());
    }

    private UUID createWarehouse(UUID farmId) {
        WarehouseEntity warehouse = new WarehouseEntity();
        warehouse.setId(UUID.randomUUID());
        warehouse.setFarmId(farmId);
        warehouse.setCode("SCOPE-" + UUID.randomUUID());
        warehouse.setName("Warehouse scope test");
        warehouse.setCreatedAt(Instant.now());
        return warehouseRepository.saveAndFlush(warehouse).getId();
    }

    private static String scopePath(UUID warehouseId) {
        return "/internal/api/v1/inventory/warehouses/" + warehouseId + "/scope";
    }
}
