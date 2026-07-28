package com.agricore.inventory;

import com.agricore.farmaccess.FarmAccessClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static io.restassured.module.mockmvc.RestAssuredMockMvc.given;
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
    @MockitoBean
    private FarmAccessClient farmAccessClient;

    @Test
    void harvestCompleted_processedTwice_stocksOnce() throws Exception {
        UUID farmId = UUID.randomUUID();
        MvcResult whResult = mockMvc.perform(post("/api/v1/inventory/warehouses")
                        .header("X-Dev-User", "wh")
                        .header("X-Dev-Roles", "WAREHOUSE_MANAGER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"farmId":"%s","code":"WH-%d","name":"Dak Lak Produce"}
                                """.formatted(farmId, System.nanoTime())))
                .andExpect(status().isCreated())
                .andReturn();
        String warehouseId = objectMapper.readTree(whResult.getResponse().getContentAsString()).get("id").asText();

        String eventId = UUID.randomUUID().toString();
        String harvestBatchId = UUID.randomUUID().toString();
        String body = """
                {
                  "eventId":"%s",
                  "harvestBatchId":"%s",
                  "farmId":"%s",
                  "warehouseId":"%s",
                  "productCode":"COFFEE-ROBUSTA",
                  "netWeightKg":3300,
                  "qualityGrade":"GRADE_A"
                }
                """.formatted(eventId, harvestBatchId, farmId, warehouseId);

        String firstResponse = given()
                .mockMvc(mockMvc)
                .header("X-Dev-User", "wh")
                .header("X-Dev-Roles", "WAREHOUSE_MANAGER")
                .contentType(ContentType.JSON)
                .body(body)
                .when()
                .post("/api/v1/inventory/events/harvest-completed")
                .then()
                .statusCode(200)
                .extract()
                .asString();

        JsonNode firstJson = objectMapper.readTree(firstResponse);
        String itemId = firstJson.get("id").asText();
        assertThat(firstJson.get("onHandQuantity").decimalValue()).isEqualByComparingTo("3300");

        // Redeliver same eventId — must not double stock
        String replayResponse = given()
                .mockMvc(mockMvc)
                .header("X-Dev-User", "wh")
                .header("X-Dev-Roles", "WAREHOUSE_MANAGER")
                .contentType(ContentType.JSON)
                .body(body)
                .when()
                .post("/api/v1/inventory/events/harvest-completed")
                .then()
                .statusCode(200)
                .extract()
                .asString();

        String itemResponse = given()
                .mockMvc(mockMvc)
                .header("X-Dev-User", "wh")
                .header("X-Dev-Roles", "WAREHOUSE_MANAGER")
                .when()
                .get("/api/v1/inventory/items/{itemId}", itemId)
                .then()
                .statusCode(200)
                .extract()
                .asString();

        assertThat(firstJson.get("sku").asText()).isEqualTo("COFFEE-ROBUSTA");
        assertThat(objectMapper.readTree(replayResponse).get("onHandQuantity").decimalValue())
                .isEqualByComparingTo("3300");
        assertThat(objectMapper.readTree(itemResponse).get("onHandQuantity").decimalValue())
                .isEqualByComparingTo("3300");
    }

    @Test
    void reserve_exactAvailableQuantitySucceeds_thenOneThousandthMoreFailsWithoutChangingAggregate() throws Exception {
        MvcResult whResult = mockMvc.perform(post("/api/v1/inventory/warehouses")
                        .header("X-Dev-User", "wh")
                        .header("X-Dev-Roles", "WAREHOUSE_MANAGER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"farmId":"%s","code":"WH2-%d","name":"Seed Store"}
                                """.formatted(UUID.randomUUID(), System.nanoTime())))
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
                                  "quantity":10,
                                  "referenceType":"SalesOrder",
                                  "referenceId":"%s"
                                }
                                """.formatted(itemId, UUID.randomUUID())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        mockMvc.perform(get("/api/v1/inventory/items/" + itemId)
                        .header("X-Dev-User", "wh")
                        .header("X-Dev-Roles", "WAREHOUSE_MANAGER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.onHandQuantity").value(10))
                .andExpect(jsonPath("$.reservedQuantity").value(10))
                .andExpect(jsonPath("$.availableQuantity").value(0));

        mockMvc.perform(post("/api/v1/inventory/reservations")
                        .header("X-Dev-User", "sales")
                        .header("X-Dev-Roles", "SALES_STAFF")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "inventoryItemId":"%s",
                                  "quantity":0.001,
                                  "referenceType":"SalesOrder",
                                  "referenceId":"%s"
                                }
                                """.formatted(itemId, UUID.randomUUID())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"));

        mockMvc.perform(get("/api/v1/inventory/items/" + itemId)
                        .header("X-Dev-User", "wh")
                        .header("X-Dev-Roles", "WAREHOUSE_MANAGER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.onHandQuantity").value(10))
                .andExpect(jsonPath("$.reservedQuantity").value(10))
                .andExpect(jsonPath("$.availableQuantity").value(0));
    }

    @Test
    void reserveThenConfirm_decrementsOnHandAndClearsReserved() throws Exception {
        MvcResult whResult = mockMvc.perform(post("/api/v1/inventory/warehouses")
                        .header("X-Dev-User", "wh")
                        .header("X-Dev-Roles", "WAREHOUSE_MANAGER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"farmId":"%s","code":"WHC-%d","name":"Confirm WH"}
                                """.formatted(UUID.randomUUID(), System.nanoTime())))
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
                                  "sku":"ROBUSTA-BAG",
                                  "name":"Robusta Bag",
                                  "itemType":"PRODUCE",
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
                                  "quantity":100,
                                  "referenceType":"Manual",
                                  "referenceId":"seed-confirm"
                                }
                                """.formatted(itemId)))
                .andExpect(status().isOk());

        MvcResult res = mockMvc.perform(post("/api/v1/inventory/reservations")
                        .header("X-Dev-User", "sales")
                        .header("X-Dev-Roles", "SALES_STAFF")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "inventoryItemId":"%s",
                                  "quantity":40,
                                  "referenceType":"SalesOrder",
                                  "referenceId":"%s"
                                }
                                """.formatted(itemId, UUID.randomUUID())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andReturn();
        String reservationId = objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(post("/api/v1/inventory/reservations/" + reservationId + "/confirm")
                        .header("X-Dev-User", "sales")
                        .header("X-Dev-Roles", "SALES_STAFF"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FULFILLED"));

        MvcResult itemAfterFirstConfirm = mockMvc.perform(get("/api/v1/inventory/items/" + itemId)
                        .header("X-Dev-User", "wh")
                        .header("X-Dev-Roles", "WAREHOUSE_MANAGER"))
                .andExpect(status().isOk())
                .andReturn();

        // Second confirm is idempotent
        mockMvc.perform(post("/api/v1/inventory/reservations/" + reservationId + "/confirm")
                        .header("X-Dev-User", "sales")
                        .header("X-Dev-Roles", "SALES_STAFF"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FULFILLED"));

        MvcResult itemAfterSecondConfirm = mockMvc.perform(get("/api/v1/inventory/items/" + itemId)
                        .header("X-Dev-User", "wh")
                        .header("X-Dev-Roles", "WAREHOUSE_MANAGER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.onHandQuantity").value(60))
                .andExpect(jsonPath("$.reservedQuantity").value(0))
                .andReturn();

        JsonNode firstConfirmedItem = objectMapper.readTree(itemAfterFirstConfirm.getResponse().getContentAsString());
        JsonNode secondConfirmedItem = objectMapper.readTree(itemAfterSecondConfirm.getResponse().getContentAsString());
        assertThat(secondConfirmedItem.get("onHandQuantity").decimalValue())
                .isEqualByComparingTo(firstConfirmedItem.get("onHandQuantity").decimalValue());
        assertThat(secondConfirmedItem.get("reservedQuantity").decimalValue())
                .isEqualByComparingTo(firstConfirmedItem.get("reservedQuantity").decimalValue());
    }
}
