package com.agricore.inventory;

import com.agricore.farmaccess.FarmAccessClient;
import com.agricore.farmaccess.FarmAccessException;
import com.agricore.inventory.api.request.CreateItemRequest;
import com.agricore.inventory.api.request.CreateWarehouseRequest;
import com.agricore.inventory.api.request.ReserveStockRequest;
import com.agricore.inventory.api.request.StockInRequest;
import com.agricore.inventory.application.service.InventoryApplicationService;
import com.agricore.inventory.infrastructure.persistence.InventoryItemJpaRepository;
import com.agricore.inventory.infrastructure.persistence.InventoryReservationJpaRepository;
import com.agricore.inventory.infrastructure.persistence.OutboxJpaRepository;
import com.agricore.inventory.infrastructure.persistence.ProcessedEventJpaRepository;
import com.agricore.inventory.infrastructure.persistence.StockMovementJpaRepository;
import com.agricore.inventory.infrastructure.persistence.WarehouseJpaRepository;
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
class InventoryFarmAccessIntegrationTest {

    private static final String USER_HEADER = "X-Dev-User";
    private static final String ROLES_HEADER = "X-Dev-Roles";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private InventoryApplicationService inventoryService;
    @Autowired
    private WarehouseJpaRepository warehouseRepository;
    @Autowired
    private InventoryItemJpaRepository itemRepository;
    @Autowired
    private InventoryReservationJpaRepository reservationRepository;
    @Autowired
    private StockMovementJpaRepository movementRepository;
    @Autowired
    private OutboxJpaRepository outboxRepository;
    @Autowired
    private ProcessedEventJpaRepository processedEventRepository;
    @MockitoBean
    private FarmAccessClient farmAccessClient;

    @Test
    void publicInventoryResourcesRejectCrossFarmAccessWithoutChangingBalances() throws Exception {
        UUID farmId = UUID.randomUUID();
        var warehouse = inventoryService.createWarehouse(new CreateWarehouseRequest(
                farmId,
                "WH-DENIED-" + System.nanoTime(),
                "Denied warehouse"
        ));
        var item = inventoryService.createItem(new CreateItemRequest(
                warehouse.id(),
                "DENIED-" + System.nanoTime(),
                "Denied item",
                "PRODUCE",
                "KG"
        ));
        inventoryService.stockIn(new StockInRequest(
                item.id(),
                new BigDecimal("20.000"),
                "Seed",
                UUID.randomUUID().toString(),
                null
        ));
        var reservation = inventoryService.reserve(new ReserveStockRequest(
                item.id(),
                new BigDecimal("5.000"),
                "SalesOrder",
                UUID.randomUUID().toString()
        ));
        long warehouseCount = warehouseRepository.count();
        long itemCount = itemRepository.count();
        long reservationCount = reservationRepository.count();
        long movementCount = movementRepository.count();
        long outboxCount = outboxRepository.count();
        long processedEventCount = processedEventRepository.count();

        doThrow(new FarmAccessException("FARM_ACCESS_DENIED", "Farm access denied", 403))
                .when(farmAccessClient)
                .requireFarm(farmId);

        expectDenied(post("/api/v1/inventory/warehouses")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"farmId":"%s","code":"BLOCKED","name":"Blocked"}
                        """.formatted(farmId)));
        expectDenied(post("/api/v1/inventory/items")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "warehouseId":"%s",
                          "sku":"BLOCKED",
                          "name":"Blocked",
                          "itemType":"PRODUCE",
                          "unit":"KG"
                        }
                        """.formatted(warehouse.id())));
        expectDenied(get("/api/v1/inventory/items/" + item.id()));
        expectDenied(post("/api/v1/inventory/stock-in")
                .contentType(MediaType.APPLICATION_JSON)
                .content(stockMutation(item.id(), "blocked-stock-in")));
        expectDenied(post("/api/v1/inventory/stock-out")
                .contentType(MediaType.APPLICATION_JSON)
                .content(stockMutation(item.id(), "blocked-stock-out")));
        expectDenied(post("/api/v1/inventory/reservations")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "inventoryItemId":"%s",
                          "quantity":1.000,
                          "referenceType":"SalesOrder",
                          "referenceId":"%s"
                        }
                        """.formatted(item.id(), UUID.randomUUID())));
        expectDenied(get("/api/v1/inventory/reservations/by-reference")
                .param("referenceType", reservation.referenceType())
                .param("referenceId", reservation.referenceId()));
        expectDenied(post("/api/v1/inventory/reservations/" + reservation.id() + "/release"));
        expectDenied(post("/api/v1/inventory/reservations/" + reservation.id() + "/confirm"));
        expectDenied(post("/api/v1/inventory/events/harvest-completed")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "eventId":"%s",
                          "harvestBatchId":"%s",
                          "warehouseId":"%s",
                          "productCode":"DENIED",
                          "netWeightKg":1.000
                        }
                        """.formatted(UUID.randomUUID(), UUID.randomUUID(), warehouse.id())));

        var unchanged = inventoryService.getItem(item.id());
        assertThat(unchanged.onHandQuantity()).isEqualByComparingTo("20.000");
        assertThat(unchanged.reservedQuantity()).isEqualByComparingTo("5.000");
        assertThat(warehouseRepository.count()).isEqualTo(warehouseCount);
        assertThat(itemRepository.count()).isEqualTo(itemCount);
        assertThat(reservationRepository.count()).isEqualTo(reservationCount);
        assertThat(movementRepository.count()).isEqualTo(movementCount);
        assertThat(outboxRepository.count()).isEqualTo(outboxCount);
        assertThat(processedEventRepository.count()).isEqualTo(processedEventCount);
    }

    private void expectDenied(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request
    ) throws Exception {
        mockMvc.perform(request
                        .header(USER_HEADER, "cross-farm-user")
                        .header(ROLES_HEADER, "SYSTEM_ADMIN,WAREHOUSE_MANAGER,SALES_STAFF"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FARM_ACCESS_DENIED"));
    }

    private static String stockMutation(UUID itemId, String referenceId) {
        return """
                {
                  "inventoryItemId":"%s",
                  "quantity":1.000,
                  "referenceType":"SecurityTest",
                  "referenceId":"%s"
                }
                """.formatted(itemId, referenceId);
    }
}
