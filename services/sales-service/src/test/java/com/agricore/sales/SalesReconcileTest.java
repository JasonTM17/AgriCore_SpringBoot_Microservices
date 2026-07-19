package com.agricore.sales;

import com.agricore.sales.domain.model.OrderStatus;
import com.agricore.sales.infrastructure.client.InventoryClient;
import com.agricore.sales.infrastructure.persistence.CustomerJpaRepository;
import com.agricore.sales.infrastructure.persistence.OrderSagaJpaRepository;
import com.agricore.sales.infrastructure.persistence.SalesOrderJpaRepository;
import com.agricore.sales.infrastructure.persistence.entity.CustomerEntity;
import com.agricore.sales.infrastructure.persistence.entity.OrderSagaEntity;
import com.agricore.sales.infrastructure.persistence.entity.SalesOrderEntity;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static com.agricore.sales.infrastructure.client.InventoryClient.ReleaseOutcome.FULFILLED;
import static com.agricore.sales.infrastructure.client.InventoryClient.ReleaseOutcome.RELEASED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Drives shipped reconcile endpoint to release a stuck reservation hold.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SalesReconcileTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private SalesOrderJpaRepository orderRepository;
    @Autowired
    private OrderSagaJpaRepository sagaRepository;
    @Autowired
    private CustomerJpaRepository customerRepository;

    @MockBean
    private InventoryClient inventoryClient;

    private UUID persistCustomer() {
        CustomerEntity c = new CustomerEntity();
        c.setId(UUID.randomUUID());
        c.setCode("C-REC-" + System.nanoTime());
        c.setName("Reconcile Customer");
        c.setCreatedAt(Instant.now());
        return customerRepository.saveAndFlush(c).getId();
    }

    private ReservedOrder persistReservedOrder(String orderPrefix, String quantity) {
        Instant now = Instant.now();
        SalesOrderEntity order = new SalesOrderEntity();
        order.setId(UUID.randomUUID());
        order.setOrderNumber(orderPrefix + "-" + System.nanoTime());
        order.setCustomerId(persistCustomer());
        order.setStatus(OrderStatus.STOCK_RESERVED);
        order.setInventoryItemId(UUID.randomUUID());
        order.setQuantity(new BigDecimal(quantity));
        order.setReservationId(UUID.randomUUID());
        order.setCorrelationId(UUID.randomUUID());
        order.setFailureReason("confirm failed; compensation pending");
        order.setCreatedAt(now);
        order.setUpdatedAt(now);
        order = orderRepository.saveAndFlush(order);

        OrderSagaEntity saga = new OrderSagaEntity();
        saga.setId(UUID.randomUUID());
        saga.setSalesOrderId(order.getId());
        saga.setCorrelationId(order.getCorrelationId());
        saga.setCurrentStep("COMPENSATION_PENDING");
        saga.setStatus("FAILED");
        saga.setRetryCount(0);
        saga.setLastError("inventory state unresolved");
        saga.setCreatedAt(now);
        saga.setUpdatedAt(now);
        sagaRepository.saveAndFlush(saga);
        return new ReservedOrder(order.getId(), order.getReservationId());
    }

    @Test
    void reconcile_release_cancelsOrderAndCallsInventoryRelease() throws Exception {
        ReservedOrder fixture = persistReservedOrder("SO-REC", "10.000");
        when(inventoryClient.release(any())).thenReturn(RELEASED);

        MvcResult result = mockMvc.perform(post("/api/v1/sales/orders/" + fixture.orderId() + "/reconcile")
                        .param("action", "RELEASE")
                        .header("X-Dev-User", "ops")
                        .header("X-Dev-Roles", "SALES_STAFF")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.sagaStatus").value("RECONCILED"))
                .andExpect(jsonPath("$.sagaStep").value("RELEASED"))
                .andReturn();

        verify(inventoryClient).release(fixture.reservationId());

        SalesOrderEntity after = orderRepository.findById(fixture.orderId()).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(after.getFailureReason()).isEqualTo("reconciled:RELEASE");

        OrderSagaEntity sagaAfter = sagaRepository.findBySalesOrderId(fixture.orderId()).orElseThrow();
        assertThat(sagaAfter.getStatus()).isEqualTo("RECONCILED");
        assertThat(sagaAfter.getRetryCount()).isEqualTo(1);

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.get("reservationId").asText()).isEqualTo(fixture.reservationId().toString());
    }

    @Test
    void reconcile_confirm_marksConfirmed() throws Exception {
        ReservedOrder fixture = persistReservedOrder("SO-CON", "5.000");
        doNothing().when(inventoryClient).confirm(any());

        mockMvc.perform(post("/api/v1/sales/orders/" + fixture.orderId() + "/reconcile")
                        .param("action", "CONFIRM")
                        .header("X-Dev-User", "ops")
                        .header("X-Dev-Roles", "WAREHOUSE_MANAGER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.sagaStatus").value("COMPLETED"));

        verify(inventoryClient).confirm(fixture.reservationId());
        assertThat(orderRepository.findById(fixture.orderId()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    void reconcile_releaseWhenInventoryAlreadyFulfilled_marksOrderConfirmed() throws Exception {
        ReservedOrder fixture = persistReservedOrder("SO-FUL", "7.000");
        when(inventoryClient.release(fixture.reservationId())).thenReturn(FULFILLED);

        mockMvc.perform(post("/api/v1/sales/orders/" + fixture.orderId() + "/reconcile")
                        .param("action", "RELEASE")
                        .header("X-Dev-User", "ops")
                        .header("X-Dev-Roles", "WAREHOUSE_MANAGER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.sagaStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.sagaStep").value("CONFIRMED"));

        assertThat(orderRepository.findById(fixture.orderId()).orElseThrow().getFailureReason()).isNull();
    }

    private record ReservedOrder(UUID orderId, UUID reservationId) {
    }
}
