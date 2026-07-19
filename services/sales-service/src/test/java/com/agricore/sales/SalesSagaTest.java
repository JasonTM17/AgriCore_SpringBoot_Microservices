package com.agricore.sales;

import com.agricore.sales.infrastructure.client.InventoryClient;
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
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static com.agricore.sales.infrastructure.client.InventoryClient.ReleaseOutcome.FULFILLED;
import static com.agricore.sales.infrastructure.client.InventoryClient.ReleaseOutcome.RELEASED;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
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

    private String createCustomer(String codePrefix) throws Exception {
        MvcResult customer = mockMvc.perform(post("/api/v1/sales/customers")
                        .header("X-Dev-User", "sales")
                        .header("X-Dev-Roles", "SALES_STAFF")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"%s-%d","name":"Saga Test Customer"}
                                """.formatted(codePrefix, System.nanoTime())))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(customer.getResponse().getContentAsString()).get("id").asText();
    }

    private ResultActions placeOrder(String orderPrefix, String customerId, int quantity) throws Exception {
        return mockMvc.perform(post("/api/v1/sales/orders")
                .header("X-Dev-User", "sales")
                .header("X-Dev-Roles", "SALES_STAFF")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "orderNumber":"%s-%d",
                          "customerId":"%s",
                          "inventoryItemId":"%s",
                          "quantity":%d
                        }
                        """.formatted(orderPrefix, System.nanoTime(), customerId, UUID.randomUUID(), quantity)));
    }

    @Test
    void placeOrder_reserveSuccess_confirms() throws Exception {
        UUID reservationId = UUID.randomUUID();
        when(inventoryClient.reserve(any(), any(), anyString())).thenReturn(reservationId);
        doNothing().when(inventoryClient).confirm(any());

        placeOrder("SO", createCustomer("C"), 100)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.sagaStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.reservationId").value(reservationId.toString()));
    }

    @Test
    void placeOrder_insufficientStock_marksOutOfStock() throws Exception {
        when(inventoryClient.reserve(any(), any(), anyString()))
                .thenThrow(new InventoryClient.InventoryReservationException(409, "INSUFFICIENT_STOCK"));

        placeOrder("SO2", createCustomer("C2"), 9999)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("OUT_OF_STOCK"))
                .andExpect(jsonPath("$.sagaStatus").value("FAILED"));
    }

    @Test
    void placeOrder_confirmFailure_releasesReservationBeforeCancelling() throws Exception {
        UUID reservationId = UUID.randomUUID();
        when(inventoryClient.reserve(any(), any(), anyString())).thenReturn(reservationId);
        doThrow(new InventoryClient.InventoryReservationException(503, "confirm unavailable"))
                .when(inventoryClient).confirm(reservationId);
        when(inventoryClient.release(reservationId)).thenReturn(RELEASED);

        placeOrder("SO3", createCustomer("C3"), 25)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.sagaStatus").value("FAILED"))
                .andExpect(jsonPath("$.sagaStep").value("COMPENSATED"));

        verify(inventoryClient).release(reservationId);
    }

    @Test
    void placeOrder_confirmResponseLost_preservesFulfilledOrder() throws Exception {
        UUID reservationId = UUID.randomUUID();
        when(inventoryClient.reserve(any(), any(), anyString())).thenReturn(reservationId);
        doThrow(new InventoryClient.InventoryReservationException(503, "confirm response lost"))
                .when(inventoryClient).confirm(reservationId);
        when(inventoryClient.release(reservationId)).thenReturn(FULFILLED);

        placeOrder("SO4", createCustomer("C4"), 30)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.sagaStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.sagaStep").value("CONFIRMED"));

        verify(inventoryClient).release(reservationId);
    }

    @Test
    void placeOrder_releaseFailure_keepsReservationPendingCompensation() throws Exception {
        UUID reservationId = UUID.randomUUID();
        when(inventoryClient.reserve(any(), any(), anyString())).thenReturn(reservationId);
        doThrow(new InventoryClient.InventoryReservationException(503, "confirm unavailable"))
                .when(inventoryClient).confirm(reservationId);
        when(inventoryClient.release(reservationId))
                .thenThrow(new IllegalStateException("release timeout"));

        placeOrder("SO5", createCustomer("C5"), 30)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("STOCK_RESERVED"))
                .andExpect(jsonPath("$.sagaStatus").value("FAILED"))
                .andExpect(jsonPath("$.sagaStep").value("COMPENSATION_PENDING"))
                .andExpect(jsonPath("$.reservationId").value(reservationId.toString()));

        verify(inventoryClient).release(reservationId);
    }
}
