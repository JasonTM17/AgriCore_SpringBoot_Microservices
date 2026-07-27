package com.agricore.sales;

import com.agricore.sales.domain.model.OrderStatus;
import com.agricore.sales.infrastructure.client.InventoryClient;
import com.agricore.sales.infrastructure.persistence.CustomerJpaRepository;
import com.agricore.sales.infrastructure.persistence.OrderSagaJpaRepository;
import com.agricore.sales.infrastructure.persistence.OutboxJpaRepository;
import com.agricore.sales.infrastructure.persistence.SalesOrderItemJpaRepository;
import com.agricore.sales.infrastructure.persistence.SalesOrderJpaRepository;
import com.agricore.sales.infrastructure.persistence.entity.CustomerEntity;
import com.agricore.sales.infrastructure.persistence.entity.OrderSagaEntity;
import com.agricore.sales.infrastructure.persistence.entity.SalesOrderEntity;
import com.agricore.sales.infrastructure.recovery.SalesSagaRecoveryJob;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SalesSagaRecoveryJobIntegrationTest {

    private static final UUID FARM_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");

    @Autowired
    private SalesSagaRecoveryJob recoveryJob;
    @Autowired
    private CustomerJpaRepository customerRepository;
    @Autowired
    private SalesOrderJpaRepository orderRepository;
    @Autowired
    private SalesOrderItemJpaRepository itemRepository;
    @Autowired
    private OrderSagaJpaRepository sagaRepository;
    @Autowired
    private OutboxJpaRepository outboxRepository;

    @MockBean
    private InventoryClient inventoryClient;

    @BeforeEach
    void clearData() {
        outboxRepository.deleteAll();
        itemRepository.deleteAll();
        sagaRepository.deleteAll();
        orderRepository.deleteAll();
        customerRepository.deleteAll();
    }

    @Test
    void retriesConfirmationAndCompletesTheSaga() {
        UUID reservationId = UUID.randomUUID();
        Fixture fixture = persist(
                OrderStatus.STOCK_RESERVED,
                "CONFIRM_INVENTORY",
                "RETRY_SCHEDULED",
                1,
                reservationId,
                Instant.now().minusSeconds(1)
        );
        doNothing().when(inventoryClient).confirm(FARM_ID, reservationId);

        recoveryJob.recover();

        assertThat(orderRepository.findById(fixture.orderId()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.CONFIRMED);
        OrderSagaEntity saga = sagaRepository.findBySalesOrderId(fixture.orderId()).orElseThrow();
        assertThat(saga.getStatus()).isEqualTo("COMPLETED");
        assertThat(saga.getRetryCount()).isEqualTo(2);
        assertThat(saga.getCompletedAt()).isNotNull();
        verify(inventoryClient).confirm(FARM_ID, reservationId);
    }

    @Test
    void resolvesAnAmbiguousReservationBeforeTryingToReserveAgain() {
        UUID reservationId = UUID.randomUUID();
        Fixture fixture = persist(
                OrderStatus.PENDING_CONFIRMATION,
                "RESERVATION_OUTCOME_UNKNOWN",
                "RETRY_SCHEDULED",
                1,
                null,
                Instant.now().minusSeconds(1)
        );
        when(inventoryClient.findByReference(FARM_ID, "SalesOrder", fixture.orderId().toString()))
                .thenReturn(Optional.of(new InventoryClient.ReservationState(
                        reservationId,
                        fixture.inventoryItemId(),
                        fixture.quantity(),
                        "ACTIVE"
                )));
        doNothing().when(inventoryClient).confirm(FARM_ID, reservationId);

        recoveryJob.recover();

        assertThat(orderRepository.findById(fixture.orderId()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.CONFIRMED);
        verify(inventoryClient, never()).reserve(any(), any(), any(), anyString());
        verify(inventoryClient).confirm(FARM_ID, reservationId);
    }

    @Test
    void compensatesAfterTheBoundedRetryBudgetIsConsumed() {
        UUID reservationId = UUID.randomUUID();
        Fixture fixture = persist(
                OrderStatus.STOCK_RESERVED,
                "CONFIRM_INVENTORY",
                "RETRY_SCHEDULED",
                4,
                reservationId,
                Instant.now().minusSeconds(1)
        );
        doThrow(new InventoryClient.InventoryReservationException(503, "confirm unavailable"))
                .when(inventoryClient).confirm(FARM_ID, reservationId);
        when(inventoryClient.release(FARM_ID, reservationId))
                .thenReturn(InventoryClient.ReleaseOutcome.RELEASED);

        recoveryJob.recover();

        assertThat(orderRepository.findById(fixture.orderId()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.CANCELLED);
        assertThat(sagaRepository.findBySalesOrderId(fixture.orderId()).orElseThrow().getStatus())
                .isEqualTo("FAILED");
        verify(inventoryClient).release(FARM_ID, reservationId);
    }

    @Test
    void compensationFailureAfterRetryBudget_requiresManualReconciliation() {
        UUID reservationId = UUID.randomUUID();
        Fixture fixture = persist(
                OrderStatus.STOCK_RESERVED,
                "CONFIRM_INVENTORY",
                "RETRY_SCHEDULED",
                4,
                reservationId,
                Instant.now().minusSeconds(1)
        );
        doThrow(new InventoryClient.InventoryReservationException(503, "confirm unavailable"))
                .when(inventoryClient).confirm(FARM_ID, reservationId);
        when(inventoryClient.release(FARM_ID, reservationId))
                .thenThrow(new InventoryClient.InventoryReservationException(503, "release unavailable"));

        recoveryJob.recover();

        assertThat(orderRepository.findById(fixture.orderId()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.STOCK_RESERVED);
        OrderSagaEntity saga = sagaRepository.findBySalesOrderId(fixture.orderId()).orElseThrow();
        assertThat(saga.getStatus()).isEqualTo("TIMED_OUT");
        assertThat(saga.getCurrentStep()).isEqualTo("MANUAL_RECONCILIATION_REQUIRED");
        verify(inventoryClient).release(FARM_ID, reservationId);
    }

    @Test
    void marksAStuckSagaTimedOutWithoutUnboundedRetries() {
        Fixture fixture = persist(
                OrderStatus.PENDING_CONFIRMATION,
                "RESERVATION_OUTCOME_UNKNOWN",
                "RETRY_SCHEDULED",
                5,
                null,
                Instant.now().minusSeconds(1)
        );

        recoveryJob.recover();

        OrderSagaEntity saga = sagaRepository.findBySalesOrderId(fixture.orderId()).orElseThrow();
        assertThat(saga.getStatus()).isEqualTo("TIMED_OUT");
        assertThat(saga.getCurrentStep()).isEqualTo("MANUAL_RECONCILIATION_REQUIRED");
        verifyNoInventoryCalls();
    }

    private Fixture persist(
            OrderStatus orderStatus,
            String step,
            String sagaStatus,
            int retryCount,
            UUID reservationId,
            Instant nextAttemptAt
    ) {
        Instant now = Instant.now();
        CustomerEntity customer = new CustomerEntity();
        customer.setId(UUID.randomUUID());
        customer.setFarmId(FARM_ID);
        customer.setCode("RECOVERY-" + UUID.randomUUID());
        customer.setName("Recovery Customer");
        customer.setCreatedAt(now);
        customerRepository.saveAndFlush(customer);

        UUID orderId = UUID.randomUUID();
        UUID inventoryItemId = UUID.randomUUID();
        BigDecimal quantity = new BigDecimal("2.500");
        SalesOrderEntity order = new SalesOrderEntity();
        order.setId(orderId);
        order.setFarmId(FARM_ID);
        order.setOrderNumber("RECOVERY-" + orderId);
        order.setCustomerId(customer.getId());
        order.setStatus(orderStatus);
        order.setInventoryItemId(inventoryItemId);
        order.setQuantity(quantity);
        order.setReservationId(reservationId);
        order.setCorrelationId(UUID.randomUUID());
        order.setCreatedAt(now);
        order.setUpdatedAt(now);
        orderRepository.saveAndFlush(order);

        OrderSagaEntity saga = new OrderSagaEntity();
        saga.setId(UUID.randomUUID());
        saga.setSalesOrderId(orderId);
        saga.setCorrelationId(order.getCorrelationId());
        saga.setCurrentStep(step);
        saga.setStatus(sagaStatus);
        saga.setRetryCount(retryCount);
        saga.setNextAttemptAt(nextAttemptAt);
        saga.setCreatedAt(now);
        saga.setUpdatedAt(now);
        sagaRepository.saveAndFlush(saga);
        return new Fixture(orderId, inventoryItemId, quantity);
    }

    private void verifyNoInventoryCalls() {
        verify(inventoryClient, never()).reserve(any(), any(), any(), anyString());
        verify(inventoryClient, never()).findByReference(any(), anyString(), anyString());
        verify(inventoryClient, never()).confirm(any(), any());
        verify(inventoryClient, never()).release(any(), any());
    }

    private record Fixture(UUID orderId, UUID inventoryItemId, BigDecimal quantity) {
    }
}
