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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
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

    @Test
    void reconcile_release_cancelsOrderAndCallsInventoryRelease() throws Exception {
        UUID reservationId = UUID.randomUUID();
        doNothing().when(inventoryClient).release(any());

        Instant now = Instant.now();
        SalesOrderEntity order = new SalesOrderEntity();
        order.setId(UUID.randomUUID());
        order.setOrderNumber("SO-REC-" + System.nanoTime());
        order.setCustomerId(persistCustomer());
        order.setStatus(OrderStatus.STOCK_RESERVED);
        order.setInventoryItemId(UUID.randomUUID());
        order.setQuantity(new BigDecimal("10.000"));
        order.setReservationId(reservationId);
        order.setCorrelationId(UUID.randomUUID());
        order.setFailureReason("confirm failed; compensation also failed");
        order.setCreatedAt(now);
        order.setUpdatedAt(now);
        order = orderRepository.saveAndFlush(order);

        OrderSagaEntity saga = new OrderSagaEntity();
        saga.setId(UUID.randomUUID());
        saga.setSalesOrderId(order.getId());
        saga.setCorrelationId(order.getCorrelationId());
        saga.setCurrentStep("CONFIRM_INVENTORY");
        saga.setStatus("FAILED");
        saga.setRetryCount(0);
        saga.setLastError("Release failed: timeout");
        saga.setCreatedAt(now);
        saga.setUpdatedAt(now);
        sagaRepository.saveAndFlush(saga);

        MvcResult result = mockMvc.perform(post("/api/v1/sales/orders/" + order.getId() + "/reconcile")
                        .param("action", "RELEASE")
                        .header("X-Dev-User", "ops")
                        .header("X-Dev-Roles", "SALES_STAFF")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.sagaStatus").value("RECONCILED"))
                .andExpect(jsonPath("$.sagaStep").value("RELEASED"))
                .andReturn();

        verify(inventoryClient).release(reservationId);

        SalesOrderEntity after = orderRepository.findById(order.getId()).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(after.getFailureReason()).isEqualTo("reconciled:RELEASE");

        OrderSagaEntity sagaAfter = sagaRepository.findBySalesOrderId(order.getId()).orElseThrow();
        assertThat(sagaAfter.getStatus()).isEqualTo("RECONCILED");
        assertThat(sagaAfter.getRetryCount()).isEqualTo(1);

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.get("reservationId").asText()).isEqualTo(reservationId.toString());
    }

    @Test
    void reconcile_confirm_marksConfirmed() throws Exception {
        UUID reservationId = UUID.randomUUID();
        doNothing().when(inventoryClient).confirm(any());

        Instant now = Instant.now();
        SalesOrderEntity order = new SalesOrderEntity();
        order.setId(UUID.randomUUID());
        order.setOrderNumber("SO-CON-" + System.nanoTime());
        order.setCustomerId(persistCustomer());
        order.setStatus(OrderStatus.STOCK_RESERVED);
        order.setInventoryItemId(UUID.randomUUID());
        order.setQuantity(new BigDecimal("5.000"));
        order.setReservationId(reservationId);
        order.setCorrelationId(UUID.randomUUID());
        order.setCreatedAt(now);
        order.setUpdatedAt(now);
        order = orderRepository.saveAndFlush(order);

        OrderSagaEntity saga = new OrderSagaEntity();
        saga.setId(UUID.randomUUID());
        saga.setSalesOrderId(order.getId());
        saga.setCorrelationId(order.getCorrelationId());
        saga.setCurrentStep("CONFIRM_INVENTORY");
        saga.setStatus("FAILED");
        saga.setRetryCount(1);
        saga.setCreatedAt(now);
        saga.setUpdatedAt(now);
        sagaRepository.saveAndFlush(saga);

        mockMvc.perform(post("/api/v1/sales/orders/" + order.getId() + "/reconcile")
                        .param("action", "CONFIRM")
                        .header("X-Dev-User", "ops")
                        .header("X-Dev-Roles", "WAREHOUSE_MANAGER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.sagaStatus").value("COMPLETED"));

        verify(inventoryClient).confirm(reservationId);
        assertThat(orderRepository.findById(order.getId()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.CONFIRMED);
    }
}
