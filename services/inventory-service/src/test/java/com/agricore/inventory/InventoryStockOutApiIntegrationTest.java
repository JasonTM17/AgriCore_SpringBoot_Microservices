package com.agricore.inventory;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InventoryStockOutApiIntegrationTest {

    private static final String USER_HEADER = "X-Dev-User";
    private static final String ROLES_HEADER = "X-Dev-Roles";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void warehouseManagerCanStockOutIdempotentlyWhileOtherRolesAreForbidden() throws Exception {
        String itemId = createStockedItem();
        String requestBody = """
                {
                  "inventoryItemId":"%s",
                  "quantity":3.500,
                  "referenceType":"WorkTask",
                  "referenceId":"TASK-API-1",
                  "note":"Applied fertilizer"
                }
                """.formatted(itemId);

        mockMvc.perform(post("/api/v1/inventory/stock-out")
                        .header(USER_HEADER, "warehouse")
                        .header(ROLES_HEADER, "WAREHOUSE_MANAGER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.onHandQuantity").value(6.5));

        mockMvc.perform(post("/api/v1/inventory/stock-out")
                        .header(USER_HEADER, "warehouse")
                        .header(ROLES_HEADER, "WAREHOUSE_MANAGER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.onHandQuantity").value(6.5));

        mockMvc.perform(post("/api/v1/inventory/stock-out")
                        .header(USER_HEADER, "viewer")
                        .header(ROLES_HEADER, "FARM_VIEWER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isForbidden());
    }

    @Test
    void stockOutRejectsDatabaseOverflowAtTheApiBoundary() throws Exception {
        String itemId = createStockedItem();

        mockMvc.perform(post("/api/v1/inventory/stock-out")
                        .header(USER_HEADER, "warehouse")
                        .header(ROLES_HEADER, "WAREHOUSE_MANAGER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "inventoryItemId":"%s",
                                  "quantity":1.0009,
                                  "referenceType":"WorkTask",
                                  "referenceId":"TASK-API-2"
                                }
                                """.formatted(itemId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    private String createStockedItem() throws Exception {
        MvcResult warehouse = mockMvc.perform(post("/api/v1/inventory/warehouses")
                        .header(USER_HEADER, "warehouse")
                        .header(ROLES_HEADER, "WAREHOUSE_MANAGER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"WH-%s","name":"API Test Warehouse"}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isCreated())
                .andReturn();
        String warehouseId = objectMapper.readTree(warehouse.getResponse().getContentAsString()).path("id").asText();

        MvcResult item = mockMvc.perform(post("/api/v1/inventory/items")
                        .header(USER_HEADER, "warehouse")
                        .header(ROLES_HEADER, "WAREHOUSE_MANAGER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "warehouseId":"%s",
                                  "sku":"SKU-%s",
                                  "name":"API Test Material",
                                  "itemType":"MATERIAL",
                                  "unit":"KG"
                                }
                                """.formatted(warehouseId, UUID.randomUUID())))
                .andExpect(status().isCreated())
                .andReturn();
        String itemId = objectMapper.readTree(item.getResponse().getContentAsString()).path("id").asText();

        mockMvc.perform(post("/api/v1/inventory/stock-in")
                        .header(USER_HEADER, "warehouse")
                        .header(ROLES_HEADER, "WAREHOUSE_MANAGER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "inventoryItemId":"%s",
                                  "quantity":10.000,
                                  "referenceType":"Seed",
                                  "referenceId":"%s"
                                }
                                """.formatted(itemId, UUID.randomUUID())))
                .andExpect(status().isOk());
        return itemId;
    }
}
