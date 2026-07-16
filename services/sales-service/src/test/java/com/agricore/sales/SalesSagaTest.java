package com.agricore.sales;

import com.agricore.sales.infrastructure.client.InventoryClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SalesSagaTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private InventoryClient inventoryClient;

    @Test
    void placeOrder_reserveSuccess_confirms() throws Exception {
        UUID reservationId = UUID.randomUUID();
        when(inventoryClient.reserve(any(), any(), anyString())).thenReturn(reservationId);

        MvcResult customer = mockMvc.perform(post("/api/v1/sales/customers")
                        .header("X-Dev-User", "sales")
                        .header("X-Dev-Roles", "SALES_STAFF")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"C-%d","name":"Cafe Export Co","email":"c@ex.com"}
                                """.formatted(System.nanoTime())))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode customerJson = objectMapper.readTree(customer.getResponse().getContentAsString());
        String customerId = customerJson.get("id").asText();

        mockMvc.perform(post("/api/v1/sales/orders")
                        .header("X-Dev-User", "sales")
                        .header("X-Dev-Roles", "SALES_STAFF")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "orderNumber":"SO-%d",
                                  "customerId":"%s",
                                  "inventoryItemId":"%s",
                                  "quantity":100
                                }
                                """.formatted(System.nanoTime(), customerId, UUID.randomUUID())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.sagaStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.reservationId").value(reservationId.toString()));
    }

    @Test
    void placeOrder_insufficientStock_marksOutOfStock() throws Exception {
        when(inventoryClient.reserve(any(), any(), anyString()))
                .thenThrow(new InventoryClient.InventoryReservationException(409, "INSUFFICIENT_STOCK"));

        MvcResult customer = mockMvc.perform(post("/api/v1/sales/customers")
                        .header("X-Dev-User", "sales")
                        .header("X-Dev-Roles", "SALES_STAFF")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"C2-%d","name":"Buyer B"}
                                """.formatted(System.nanoTime())))
                .andExpect(status().isCreated())
                .andReturn();
        String customerId = objectMapper.readTree(customer.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(post("/api/v1/sales/orders")
                        .header("X-Dev-User", "sales")
                        .header("X-Dev-Roles", "SALES_STAFF")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "orderNumber":"SO2-%d",
                                  "customerId":"%s",
                                  "inventoryItemId":"%s",
                                  "quantity":9999
                                }
                                """.formatted(System.nanoTime(), customerId, UUID.randomUUID())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("OUT_OF_STOCK"))
                .andExpect(jsonPath("$.sagaStatus").value("FAILED"));
    }
}
