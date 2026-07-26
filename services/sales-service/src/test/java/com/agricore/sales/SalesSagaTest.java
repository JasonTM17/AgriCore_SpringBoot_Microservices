package com.agricore.sales;

import com.agricore.sales.infrastructure.client.InventoryClient;
import com.agricore.sales.infrastructure.persistence.OrderSagaJpaRepository;
import com.agricore.sales.infrastructure.persistence.SalesOrderItemJpaRepository;
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
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
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
    @Autowired
    private SalesOrderItemJpaRepository itemRepository;
    @Autowired
    private OrderSagaJpaRepository sagaRepository;

    @MockBean
    private InventoryClient inventoryClient;

    private String createCustomer(String codePrefix) throws Exception {
        MvcResult customer = mockMvc.perform(post("/api/v1/sales/customers")
                        .header("X-Dev-User", "sales")
                        .header("X-Dev-Roles", "SALES_STAFF")
                        .header("X-Dev-Permissions", "SALES_WRITE,SALES_READ")
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
                .header("X-Dev-Permissions", "SALES_WRITE,SALES_READ")
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
    void placeOrder_referenceConflictDoesNotClaimStockIsUnavailable() throws Exception {
        when(inventoryClient.reserve(any(), any(), anyString()))
                .thenThrow(new InventoryClient.InventoryReservationException(
                        409,
                        "RESERVATION_REFERENCE_CONFLICT"
                ));

        placeOrder("SO-REF", createCustomer("C-REF"), 10)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.sagaStatus").value("FAILED"));
    }

    @Test
    void placeOrder_confirmFailure_schedulesDurableRetry() throws Exception {
        UUID reservationId = UUID.randomUUID();
        when(inventoryClient.reserve(any(), any(), anyString())).thenReturn(reservationId);
        doThrow(new InventoryClient.InventoryReservationException(503, "confirm unavailable"))
                .when(inventoryClient).confirm(reservationId);
        when(inventoryClient.release(reservationId)).thenReturn(RELEASED);

        placeOrder("SO3", createCustomer("C3"), 25)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("STOCK_RESERVED"))
                .andExpect(jsonPath("$.sagaStatus").value("RETRY_SCHEDULED"))
                .andExpect(jsonPath("$.sagaStep").value("CONFIRM_INVENTORY"));

        verify(inventoryClient, never()).release(reservationId);
    }

    @Test
    void placeOrder_confirmResponseLost_isRecoverableWithoutImmediateCompensation() throws Exception {
        UUID reservationId = UUID.randomUUID();
        when(inventoryClient.reserve(any(), any(), anyString())).thenReturn(reservationId);
        doThrow(new InventoryClient.InventoryReservationException(503, "confirm response lost"))
                .when(inventoryClient).confirm(reservationId);
        when(inventoryClient.release(reservationId)).thenReturn(FULFILLED);

        placeOrder("SO4", createCustomer("C4"), 30)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("STOCK_RESERVED"))
                .andExpect(jsonPath("$.sagaStatus").value("RETRY_SCHEDULED"))
                .andExpect(jsonPath("$.sagaStep").value("CONFIRM_INVENTORY"));

        verify(inventoryClient, never()).release(reservationId);
    }

    @Test
    void placeOrder_confirmFailure_doesNotReleaseBeforeRetryBudgetIsConsumed() throws Exception {
        UUID reservationId = UUID.randomUUID();
        when(inventoryClient.reserve(any(), any(), anyString())).thenReturn(reservationId);
        doThrow(new InventoryClient.InventoryReservationException(503, "confirm unavailable"))
                .when(inventoryClient).confirm(reservationId);
        placeOrder("SO5", createCustomer("C5"), 30)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("STOCK_RESERVED"))
                .andExpect(jsonPath("$.sagaStatus").value("RETRY_SCHEDULED"))
                .andExpect(jsonPath("$.sagaStep").value("CONFIRM_INVENTORY"))
                .andExpect(jsonPath("$.reservationId").value(reservationId.toString()));

        verify(inventoryClient, never()).release(reservationId);
    }

    @Test
    void placeOrder_persistsV1ItemAndOptionalPriceSnapshot() throws Exception {
        UUID reservationId = UUID.randomUUID();
        when(inventoryClient.reserve(any(), any(), anyString())).thenReturn(reservationId);
        doNothing().when(inventoryClient).confirm(any());
        String customerId = createCustomer("PRICE");
        UUID itemId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/sales/orders")
                        .header("X-Dev-User", "sales")
                        .header("X-Dev-Roles", "SALES_STAFF")
                        .header("X-Dev-Permissions", "SALES_WRITE,SALES_READ")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "orderNumber":"PRICE-%d",
                                  "customerId":"%s",
                                  "inventoryItemId":"%s",
                                  "quantity":2.500,
                                  "unitPrice":12.3456,
                                  "currencyCode":"usd"
                                }
                                """.formatted(System.nanoTime(), customerId, itemId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.currencyCode").value("USD"))
                .andExpect(jsonPath("$.subtotalAmount").value(30.864))
                .andExpect(jsonPath("$.totalAmount").value(30.864))
                .andExpect(jsonPath("$.items[0].unitPrice").value(12.3456))
                .andExpect(jsonPath("$.items[0].lineTotal").value(30.864));

        assertThat(itemRepository.findAll()).anySatisfy(item -> {
            assertThat(item.getInventoryItemId()).isEqualTo(itemId);
            assertThat(item.getQuantity()).isEqualByComparingTo("2.500");
            assertThat(item.getLineTotal()).isEqualByComparingTo("30.8640");
        });
    }

    @Test
    void placeOrder_unknownReserveResponse_isDurablyMarkedForReconciliation() throws Exception {
        when(inventoryClient.reserve(any(), any(), anyString()))
                .thenThrow(new InventoryClient.InventoryReservationException(503, "reserve timeout"));
        when(inventoryClient.findByReference(anyString(), anyString()))
                .thenThrow(new InventoryClient.InventoryReservationException(503, "lookup timeout"));

        String customerId = createCustomer("UNKNOWN");
        placeOrder("UNKNOWN", customerId, 3)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING_CONFIRMATION"))
                .andExpect(jsonPath("$.sagaStatus").value("RETRY_SCHEDULED"))
                .andExpect(jsonPath("$.sagaStep").value("RESERVATION_OUTCOME_UNKNOWN"));

        assertThat(sagaRepository.findAll()).anySatisfy(saga ->
                assertThat(saga.getCurrentStep()).isEqualTo("RESERVATION_OUTCOME_UNKNOWN"));
    }
}
