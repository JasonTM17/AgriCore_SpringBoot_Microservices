package com.agricore.inventory;

import com.agricore.farmaccess.FarmAccessClient;
import com.agricore.farmaccess.FarmAccessException;
import com.agricore.inventory.api.request.CreateItemRequest;
import com.agricore.inventory.api.request.CreateWarehouseRequest;
import com.agricore.inventory.api.request.StockInRequest;
import com.agricore.inventory.application.service.InventoryApplicationService;
import com.agricore.inventory.infrastructure.persistence.StockMovementJpaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InventoryFarmAccessUnavailableIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private InventoryApplicationService inventoryService;
    @Autowired
    private StockMovementJpaRepository movementRepository;
    @MockitoBean
    private FarmAccessClient farmAccessClient;

    @Test
    void farmAccessOutageFailsClosedForReadsAndMutations() throws Exception {
        UUID farmId = UUID.randomUUID();
        var warehouse = inventoryService.createWarehouse(new CreateWarehouseRequest(
                farmId,
                "WH-OUTAGE-" + System.nanoTime(),
                "Outage warehouse"
        ));
        var item = inventoryService.createItem(new CreateItemRequest(
                warehouse.id(),
                "OUTAGE-" + System.nanoTime(),
                "Outage item",
                "PRODUCE",
                "KG"
        ));
        inventoryService.stockIn(new StockInRequest(
                item.id(),
                new BigDecimal("8.000"),
                "Seed",
                UUID.randomUUID().toString(),
                null
        ));
        long movementCount = movementRepository.count();

        doThrow(new FarmAccessException(
                "FARM_ACCESS_UNAVAILABLE",
                "Farm access verification is temporarily unavailable",
                503
        )).when(farmAccessClient).requireFarm(farmId);

        expectUnavailable(get("/api/v1/inventory/items/" + item.id()));
        expectUnavailable(post("/api/v1/inventory/stock-out")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "inventoryItemId":"%s",
                          "quantity":1.000,
                          "referenceType":"SecurityTest",
                          "referenceId":"farm-access-outage"
                        }
                        """.formatted(item.id())));

        assertThat(inventoryService.getItem(item.id()).onHandQuantity())
                .isEqualByComparingTo("8.000");
        assertThat(movementRepository.count()).isEqualTo(movementCount);
    }

    @Test
    void legacyWarehouseWithoutFarmScopeFailsClosed() throws Exception {
        var warehouse = inventoryService.createWarehouse(new CreateWarehouseRequest(
                null,
                "WH-LEGACY-" + System.nanoTime(),
                "Legacy warehouse"
        ));
        var item = inventoryService.createItem(new CreateItemRequest(
                warehouse.id(),
                "LEGACY-" + System.nanoTime(),
                "Legacy item",
                "PRODUCE",
                "KG"
        ));

        mockMvc.perform(get("/api/v1/inventory/items/" + item.id())
                        .header("X-Dev-User", "legacy-user")
                        .header("X-Dev-Roles", "WAREHOUSE_MANAGER"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("WAREHOUSE_SCOPE_UNAVAILABLE"));
    }

    private void expectUnavailable(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request
    ) throws Exception {
        mockMvc.perform(request
                        .header("X-Dev-User", "farm-user")
                        .header("X-Dev-Roles", "WAREHOUSE_MANAGER"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("FARM_ACCESS_UNAVAILABLE"));
    }
}
