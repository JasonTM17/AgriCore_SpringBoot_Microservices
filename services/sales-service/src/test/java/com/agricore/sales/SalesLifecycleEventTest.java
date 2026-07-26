package com.agricore.sales;

import com.agricore.farmaccess.FarmAccessClient;
import com.agricore.sales.application.service.SalesOrderTransactionService;
import com.agricore.sales.domain.model.OrderStatus;
import com.agricore.sales.infrastructure.client.InventoryClient;
import com.agricore.sales.infrastructure.persistence.CustomerJpaRepository;
import com.agricore.sales.infrastructure.persistence.OrderSagaJpaRepository;
import com.agricore.sales.infrastructure.persistence.OutboxJpaRepository;
import com.agricore.sales.infrastructure.persistence.SalesOrderJpaRepository;
import com.agricore.sales.infrastructure.persistence.entity.CustomerEntity;
import com.agricore.sales.infrastructure.persistence.entity.OrderSagaEntity;
import com.agricore.sales.infrastructure.persistence.entity.OutboxEventEntity;
import com.agricore.sales.infrastructure.persistence.entity.SalesOrderEntity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SalesLifecycleEventTest {

    private static final UUID FARM_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private OutboxJpaRepository outboxRepository;
    @Autowired
    private CustomerJpaRepository customerRepository;
    @Autowired
    private SalesOrderJpaRepository orderRepository;
    @Autowired
    private OrderSagaJpaRepository sagaRepository;
    @Autowired
    private SalesOrderTransactionService transactions;

    @MockBean
    private InventoryClient inventoryClient;
    @MockBean
    private FarmAccessClient farmAccessClient;

    @BeforeEach
    void clearOutbox() {
        outboxRepository.deleteAll();
    }

    @Test
    void successfulOrderEmitsCreatedAndConfirmedWithCorrelation() throws Exception {
        UUID reservationId = UUID.randomUUID();
        when(inventoryClient.reserve(any(), any(), any(), anyString())).thenReturn(reservationId);
        doNothing().when(inventoryClient).confirm(FARM_ID, reservationId);

        UUID customerId = createCustomer();
        mockMvc.perform(post("/api/v1/sales/orders")
                        .header("X-Dev-User", "sales")
                        .header("X-Dev-Roles", "SALES_STAFF")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderJson(customerId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        List<OutboxEventEntity> events = salesEvents();
        assertThat(events).extracting(OutboxEventEntity::getEventType)
                .containsExactly("SalesOrderCreated.v1", "SalesOrderConfirmed.v1");
        JsonNode created = objectMapper.readTree(events.get(0).getPayload());
        JsonNode confirmed = objectMapper.readTree(events.get(1).getPayload());
        assertThat(created.path("correlationId").asText()).isNotBlank();
        assertThat(confirmed.path("correlationId").asText())
                .isEqualTo(created.path("correlationId").asText());
        assertThat(confirmed.path("payload").path("reservationId").asText())
                .isEqualTo(reservationId.toString());
    }

    @Test
    void reservationFailureEmitsCancellationWithFinalStatus() throws Exception {
        when(inventoryClient.reserve(any(), any(), any(), anyString()))
                .thenThrow(new InventoryClient.InventoryReservationException(409, "INSUFFICIENT_STOCK"));

        UUID customerId = createCustomer();
        mockMvc.perform(post("/api/v1/sales/orders")
                        .header("X-Dev-User", "sales")
                        .header("X-Dev-Roles", "SALES_STAFF")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderJson(customerId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("OUT_OF_STOCK"));

        List<OutboxEventEntity> events = salesEvents();
        assertThat(events).extracting(OutboxEventEntity::getEventType)
                .containsExactly("SalesOrderCreated.v1", "SalesOrderCancelled.v1");
        JsonNode cancelled = objectMapper.readTree(events.get(1).getPayload());
        assertThat(cancelled.path("payload").path("finalStatus").asText()).isEqualTo("OUT_OF_STOCK");
        assertThat(cancelled.path("payload").path("reasonCode").asText()).isEqualTo("INSUFFICIENT_STOCK");
    }

    @Test
    void confirmingTheSameOrderTwiceEmitsOnlyOneConfirmedEvent() {
        UUID orderId = persistReservedOrder();
        UUID reservationId = orderRepository.findById(orderId).orElseThrow().getReservationId();

        transactions.confirm(orderId, reservationId, false);
        transactions.confirm(orderId, reservationId, true);

        assertThat(salesEvents()).extracting(OutboxEventEntity::getEventType)
                .containsExactly("SalesOrderConfirmed.v1");
        assertThat(orderRepository.findById(orderId).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.CONFIRMED);
    }

    private UUID createCustomer() throws Exception {
        var result = mockMvc.perform(post("/api/v1/sales/customers")
                        .header("X-Dev-User", "sales")
                        .header("X-Dev-Roles", "SALES_STAFF")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"farmId\":\"" + FARM_ID + "\",\"code\":\"EVT-"
                                + System.nanoTime() + "\",\"name\":\"Event Customer\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
    }

    private String orderJson(UUID customerId) {
        return "{\"orderNumber\":\"EVT-ORDER-" + System.nanoTime()
                + "\",\"farmId\":\"" + FARM_ID
                + "\",\"customerId\":\"" + customerId
                + "\",\"inventoryItemId\":\"" + UUID.randomUUID()
                + "\",\"quantity\":10}";
    }

    private UUID persistReservedOrder() {
        Instant now = Instant.now();
        CustomerEntity customer = new CustomerEntity();
        customer.setId(UUID.randomUUID());
        customer.setFarmId(FARM_ID);
        customer.setCode("EVT-DIRECT-" + System.nanoTime());
        customer.setName("Direct Customer");
        customer.setCreatedAt(now);
        customer = customerRepository.saveAndFlush(customer);

        SalesOrderEntity order = new SalesOrderEntity();
        order.setId(UUID.randomUUID());
        order.setFarmId(FARM_ID);
        order.setOrderNumber("EVT-DIRECT-ORDER-" + System.nanoTime());
        order.setCustomerId(customer.getId());
        order.setStatus(OrderStatus.STOCK_RESERVED);
        order.setInventoryItemId(UUID.randomUUID());
        order.setQuantity(new BigDecimal("10.000"));
        order.setReservationId(UUID.randomUUID());
        order.setCorrelationId(UUID.randomUUID());
        order.setCreatedAt(now);
        order.setUpdatedAt(now);
        order = orderRepository.saveAndFlush(order);

        OrderSagaEntity saga = new OrderSagaEntity();
        saga.setId(UUID.randomUUID());
        saga.setSalesOrderId(order.getId());
        saga.setCorrelationId(order.getCorrelationId());
        saga.setCurrentStep("CONFIRM_INVENTORY");
        saga.setStatus("RUNNING");
        saga.setRetryCount(0);
        saga.setCreatedAt(now);
        saga.setUpdatedAt(now);
        sagaRepository.saveAndFlush(saga);
        return order.getId();
    }

    private List<OutboxEventEntity> salesEvents() {
        return outboxRepository.findAll().stream()
                .filter(event -> "SalesOrder".equals(event.getAggregateType()))
                .sorted(Comparator.comparing(OutboxEventEntity::getCreatedAt))
                .toList();
    }
}
