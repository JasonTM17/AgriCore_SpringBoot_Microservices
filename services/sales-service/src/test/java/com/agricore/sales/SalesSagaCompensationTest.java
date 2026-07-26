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

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A saga that records itself COMPENSATED must actually have compensated.
 *
 * <p>Failures at the confirm step arrive as {@code InventoryReservationException}, the same type
 * used for a stock shortage at the reserve step. Branching on the exception type alone sent confirm
 * failures down a path that never released, so inventory held the stock indefinitely while the saga
 * row claimed compensation had run.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SalesSagaCompensationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private InventoryClient inventoryClient;

    private String createCustomer(String prefix) throws Exception {
        MvcResult customer = mockMvc.perform(post("/api/v1/sales/customers")
                        .header("X-Dev-User", "sales")
                        .header("X-Dev-Roles", "SALES_STAFF")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"%s-%d","name":"Compensation Buyer"}
                                """.formatted(prefix, System.nanoTime())))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(customer.getResponse().getContentAsString()).get("id").asText();
    }

    private org.springframework.test.web.servlet.ResultActions placeOrder(String prefix, String customerId)
            throws Exception {
        return mockMvc.perform(post("/api/v1/sales/orders")
                .header("X-Dev-User", "sales")
                .header("X-Dev-Roles", "SALES_STAFF")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "orderNumber":"%s-%d",
                          "customerId":"%s",
                          "inventoryItemId":"%s",
                          "quantity":10
                        }
                        """.formatted(prefix, System.nanoTime(), customerId, UUID.randomUUID())));
    }

    @Test
    void confirmFailure_releasesTheReservation() throws Exception {
        UUID reservationId = UUID.randomUUID();
        when(inventoryClient.reserve(any(), any(), anyString())).thenReturn(reservationId);
        doThrow(new InventoryClient.InventoryReservationException(500, "RESERVATION_CONFIRM_FAILED"))
                .when(inventoryClient).confirm(any());
        doNothing().when(inventoryClient).release(any());

        placeOrder("SOC", createCustomer("CC"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        verify(inventoryClient).release(eq(reservationId));
    }

    @Test
    void reserveFailure_releasesNothing_becauseNothingWasReserved() throws Exception {
        when(inventoryClient.reserve(any(), any(), anyString()))
                .thenThrow(new InventoryClient.InventoryReservationException(409, "INSUFFICIENT_STOCK"));

        placeOrder("SOR", createCustomer("CR"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("OUT_OF_STOCK"));

        verify(inventoryClient, never()).release(any());
    }

    /**
     * Inventory answers 409 for an optimistic-lock clash as well as for a stock shortage. Treating
     * the status alone as "out of stock" terminally cancelled the loser of a concurrent update even
     * though the stock was there.
     */
    @Test
    void optimisticLockConflict_isNotReportedAsOutOfStock() throws Exception {
        when(inventoryClient.reserve(any(), any(), anyString()))
                .thenThrow(new InventoryClient.InventoryReservationException(
                        409, "{\"code\":\"OPTIMISTIC_LOCK\",\"message\":\"Concurrent stock update conflict; retry the request\"}"));

        placeOrder("SOL", createCustomer("CL"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    /**
     * Compensation runs while another failure is already being handled. A failing release must not
     * replace the original cause or abandon the order mid-update.
     */
    @Test
    void releaseFailureDuringCompensation_stillCancelsTheOrder() throws Exception {
        when(inventoryClient.reserve(any(), any(), anyString())).thenReturn(UUID.randomUUID());
        doThrow(new InventoryClient.InventoryReservationException(503, "CONFIRM_UNAVAILABLE"))
                .when(inventoryClient).confirm(any());
        doThrow(new RuntimeException("inventory unreachable")).when(inventoryClient).release(any());

        placeOrder("SOF", createCustomer("CF"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.sagaStatus").value("FAILED"));
    }
}
