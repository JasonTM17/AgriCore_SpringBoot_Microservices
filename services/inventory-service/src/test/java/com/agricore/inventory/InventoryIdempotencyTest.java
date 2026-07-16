package com.agricore.inventory;

import com.fasterxml.jackson.databind.JsonNode;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InventoryIdempotencyTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void harvestCompleted_processedTwice_stocksOnce() throws Exception {
        MvcResult whResult = mockMvc.perform(post("/api/v1/inventory/warehouses")
                        .header("X-Dev-User", "wh")
                        .header("X-Dev-Roles", "WAREHOUSE_MANAGER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"WH-%d","name":"Dak Lak Produce"}
                                """.formatted(System.nanoTime())))
                .andExpect(status().isCreated())
                .andReturn();
        String warehouseId = objectMapper.readTree(whResult.getResponse().getContentAsString()).get("id").asText();

        String eventId = UUID.randomUUID().toString();
        String harvestBatchId = UUID.randomUUID().toString();
        String body = """
                {
                  "eventId":"%s",
                  "harvestBatchId":"%s",
                  "warehouseId":"%s",
                  "productCode":"COFFEE-ROBUSTA",
                  "netWeightKg":3300,
                  "qualityGrade":"GRADE_A"
                }
                """.formatted(eventId, harvestBatchId, warehouseId);

        MvcResult first = mockMvc.perform(post("/api/v1/inventory/events/harvest-completed")
                        .header("X-Dev-User", "wh")
                        .header("X-Dev-Roles", "WAREHOUSE_MANAGER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.onHandQuantity").value(3300))
                .andReturn();

        JsonNode firstJson = objectMapper.readTree(first.getResponse().getContentAsString());
        String itemId = firstJson.get("id").asText();

        // Redeliver same eventId — must not double stock
        mockMvc.perform(post("/api/v1/inventory/events/harvest-completed")
                        .header("X-Dev-User", "wh")
                        .header("X-Dev-Roles", "WAREHOUSE_MANAGER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.onHandQuantity").value(3300));

        mockMvc.perform(get("/api/v1/inventory/items/" + itemId)
                        .header("X-Dev-User", "wh")
                        .header("X-Dev-Roles", "WAREHOUSE_MANAGER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.onHandQuantity").value(3300));

        assertThat(firstJson.get("sku").asText()).isEqualTo("COFFEE-ROBUSTA");
    }

    @Test
    void reserve_insufficientStock_fails() throws Exception {
        MvcResult whResult = mockMvc.perform(post("/api/v1/inventory/warehouses")
                        .header("X-Dev-User", "wh")
                        .header("X-Dev-Roles", "WAREHOUSE_MANAGER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"WH2-%d","name":"Seed Store"}
                                """.formatted(System.nanoTime())))
                .andExpect(status().isCreated())
                .andReturn();
        String warehouseId = objectMapper.readTree(whResult.getResponse().getContentAsString()).get("id").asText();

        MvcResult itemResult = mockMvc.perform(post("/api/v1/inventory/items")
                        .header("X-Dev-User", "wh")
                        .header("X-Dev-Roles", "WAREHOUSE_MANAGER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "warehouseId":"%s",
                                  "sku":"FERT-NPK",
                                  "name":"NPK Fertilizer",
                                  "itemType":"MATERIAL",
                                  "unit":"KG"
                                }
                                """.formatted(warehouseId)))
                .andExpect(status().isCreated())
                .andReturn();
        String itemId = objectMapper.readTree(itemResult.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(post("/api/v1/inventory/stock-in")
                        .header("X-Dev-User", "wh")
                        .header("X-Dev-Roles", "WAREHOUSE_MANAGER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "inventoryItemId":"%s",
                                  "quantity":10,
                                  "referenceType":"Manual",
                                  "referenceId":"seed-1"
                                }
                                """.formatted(itemId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.onHandQuantity").value(10));

        mockMvc.perform(post("/api/v1/inventory/reservations")
                        .header("X-Dev-User", "sales")
                        .header("X-Dev-Roles", "SALES_STAFF")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "inventoryItemId":"%s",
                                  "quantity":50,
                                  "referenceType":"SalesOrder",
                                  "referenceId":"%s"
                                }
                                """.formatted(itemId, UUID.randomUUID())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"));
    }
}
