package com.agricore.inventory;

import com.agricore.farmaccess.FarmAccessClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InventoryStockOutApiIntegrationTest {

    private static final String USER_HEADER = "X-Dev-User";
    private static final String ROLES_HEADER = "X-Dev-Roles";
    private static final String INTERNAL_TOKEN_HEADER = "X-Internal-Service-Token";
    private static final String INTERNAL_TOKEN = "test-inventory-work-service-token-012345678901234567890123";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @MockitoBean
    private FarmAccessClient farmAccessClient;

    @ParameterizedTest
    @ValueSource(strings = {"", "INVENTORY_READ"})
    void matchingWarehouseRoleWithoutWritePermissionCannotStockOut(String explicitPermissions) throws Exception {
        mockMvc.perform(post("/api/v1/inventory/stock-out")
                        .header(USER_HEADER, "warehouse")
                        .header(ROLES_HEADER, "WAREHOUSE_MANAGER")
                        .header("X-Dev-Permissions", explicitPermissions)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "inventoryItemId":"%s",
                                  "quantity":1.000,
                                  "referenceType":"WorkTask",
                                  "referenceId":"PERMISSION-BOUNDARY"
                                }
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isForbidden());
    }

    @Test
    void warehouseManagerCanStockOutIdempotentlyWhileOtherRolesAreForbidden() throws Exception {
        UUID farmId = UUID.randomUUID();
        String itemId = createStockedItem(farmId);
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

        mockMvc.perform(post("/internal/api/v1/inventory/stock-out")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/internal/api/v1/inventory/stock-out")
                        .header(USER_HEADER, "worker")
                        .header(ROLES_HEADER, "FIELD_WORKER")
                        .header(INTERNAL_TOKEN_HEADER, INTERNAL_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "farmId":"%s",
                                  "inventoryItemId":"%s",
                                  "quantity":1.000,
                                  "referenceType":"WorkTask",
                                  "referenceId":"TASK-API-WRONG-FARM"
                                }
                                """.formatted(UUID.randomUUID(), itemId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ITEM_NOT_FOUND"));

        mockMvc.perform(post("/internal/api/v1/inventory/stock-out")
                        .header(USER_HEADER, "worker")
                        .header(ROLES_HEADER, "FIELD_WORKER")
                        .header(INTERNAL_TOKEN_HEADER, INTERNAL_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "farmId":"%s",
                                  "inventoryItemId":"%s",
                                  "quantity":1.000,
                                  "referenceType":"WorkTask",
                                  "referenceId":"TASK-API-INTERNAL"
                                }
                                """.formatted(farmId, itemId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.onHandQuantity").value(5.5));

        mockMvc.perform(post("/internal/api/v1/inventory/stock-out")
                        .header(USER_HEADER, "worker")
                        .header(ROLES_HEADER, "FIELD_WORKER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "farmId":"%s",
                                  "inventoryItemId":"%s",
                                  "quantity":1.000,
                                  "referenceType":"WorkTask",
                                  "referenceId":"TASK-API-NO-SERVICE-TOKEN"
                                }
                                """.formatted(farmId, itemId)))
                .andExpect(status().isForbidden());
    }

    @Test
    void internalServiceTokenSupportsReservationRecoveryWithoutAUserSession() throws Exception {
        UUID farmId = UUID.randomUUID();
        String itemId = createStockedItem(farmId);
        String referenceId = "SO-" + UUID.randomUUID();
        MvcResult reserved = mockMvc.perform(post("/internal/api/v1/inventory/reservations")
                        .header(INTERNAL_TOKEN_HEADER, INTERNAL_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "farmId":"%s",
                                  "inventoryItemId":"%s",
                                  "quantity":2.500,
                                  "referenceType":"SalesOrder",
                                  "referenceId":"%s"
                                }
                                """.formatted(farmId, itemId, referenceId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andReturn();
        String reservationId = objectMapper.readTree(
                reserved.getResponse().getContentAsString()
        ).path("id").asText();

        mockMvc.perform(get("/internal/api/v1/inventory/reservations/by-reference")
                .header(INTERNAL_TOKEN_HEADER, INTERNAL_TOKEN)
                        .param("farmId", farmId.toString())
                        .param("referenceType", "SalesOrder")
                        .param("referenceId", referenceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(reservationId));

        mockMvc.perform(post("/internal/api/v1/inventory/reservations/{id}/confirm", reservationId)
                        .header(INTERNAL_TOKEN_HEADER, INTERNAL_TOKEN)
                        .param("farmId", farmId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FULFILLED"));

                mockMvc.perform(get("/internal/api/v1/inventory/reservations/by-reference")
                        .param("farmId", farmId.toString())
                        .param("referenceType", "SalesOrder")
                        .param("referenceId", referenceId))
                .andExpect(status().isForbidden());
    }

    @Test
    void stockOutRejectsDatabaseOverflowAtTheApiBoundary() throws Exception {
        String itemId = createStockedItem(UUID.randomUUID());

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

    private String createStockedItem(UUID farmId) throws Exception {
        MvcResult warehouse = mockMvc.perform(post("/api/v1/inventory/warehouses")
                        .header(USER_HEADER, "warehouse")
                        .header(ROLES_HEADER, "WAREHOUSE_MANAGER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"farmId":"%s","code":"WH-%s","name":"API Test Warehouse"}
                                """.formatted(farmId, UUID.randomUUID())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.farmId").value(farmId.toString()))
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
