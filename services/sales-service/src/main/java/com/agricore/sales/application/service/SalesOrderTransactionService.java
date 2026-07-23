package com.agricore.sales.application.service;

import com.agricore.sales.api.request.CreateOrderRequest;
import com.agricore.sales.domain.exception.SalesException;
import com.agricore.sales.domain.model.OrderStatus;
import com.agricore.sales.infrastructure.persistence.CustomerJpaRepository;
import com.agricore.sales.infrastructure.persistence.OrderSagaJpaRepository;
import com.agricore.sales.infrastructure.persistence.SalesOrderItemJpaRepository;
import com.agricore.sales.infrastructure.persistence.SalesOrderJpaRepository;
import com.agricore.sales.infrastructure.persistence.entity.OrderSagaEntity;
import com.agricore.sales.infrastructure.persistence.entity.SalesOrderEntity;
import com.agricore.sales.infrastructure.persistence.entity.SalesOrderItemEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Service
public class SalesOrderTransactionService {

    private final CustomerJpaRepository customerRepository;
    private final SalesOrderJpaRepository orderRepository;
    private final OrderSagaJpaRepository sagaRepository;
    private final SalesOrderItemJpaRepository itemRepository;
    private final SalesEventOutboxWriter outboxWriter;

    public SalesOrderTransactionService(
            CustomerJpaRepository customerRepository,
            SalesOrderJpaRepository orderRepository,
            OrderSagaJpaRepository sagaRepository,
            SalesOrderItemJpaRepository itemRepository,
            SalesEventOutboxWriter outboxWriter
    ) {
        this.customerRepository = customerRepository;
        this.orderRepository = orderRepository;
        this.sagaRepository = sagaRepository;
        this.itemRepository = itemRepository;
        this.outboxWriter = outboxWriter;
    }

    @Transactional
    public UUID createOrder(CreateOrderRequest request, String orderNumber) {
        if (!customerRepository.existsById(request.customerId())) {
            throw new SalesException("CUSTOMER_NOT_FOUND", "Customer not found", 404);
        }
        if (orderRepository.existsByOrderNumberIgnoreCase(orderNumber)) {
            throw new SalesException("ORDER_EXISTS", "Order number already exists", 409);
        }
        validatePriceSnapshot(request);

        Instant now = Instant.now();
        UUID correlationId = UUID.randomUUID();
        SalesOrderEntity order = new SalesOrderEntity();
        order.setId(UUID.randomUUID());
        order.setOrderNumber(orderNumber);
        order.setCustomerId(request.customerId());
        order.setStatus(OrderStatus.PENDING_CONFIRMATION);
        order.setInventoryItemId(request.inventoryItemId());
        order.setQuantity(request.quantity());
        order.setCurrencyCode(normalizeCurrency(request.currencyCode()));
        BigDecimal lineTotal = request.unitPrice() == null
                ? null
                : request.quantity().multiply(request.unitPrice()).setScale(4, RoundingMode.HALF_UP);
        order.setSubtotalAmount(lineTotal);
        order.setTotalAmount(lineTotal);
        order.setCorrelationId(correlationId);
        order.setCreatedAt(now);
        order.setUpdatedAt(now);
        orderRepository.save(order);

        SalesOrderItemEntity item = new SalesOrderItemEntity();
        item.setId(UUID.randomUUID());
        item.setSalesOrderId(order.getId());
        item.setLineNumber(1);
        item.setInventoryItemId(request.inventoryItemId());
        item.setQuantity(request.quantity());
        item.setUnitPrice(request.unitPrice());
        item.setLineTotal(lineTotal);
        item.setCurrencyCode(order.getCurrencyCode());
        item.setCreatedAt(now);
        itemRepository.save(item);

        OrderSagaEntity saga = new OrderSagaEntity();
        saga.setId(UUID.randomUUID());
        saga.setSalesOrderId(order.getId());
        saga.setCorrelationId(correlationId);
        saga.setCurrentStep("RESERVE_INVENTORY");
        saga.setStatus("RUNNING");
        saga.setRetryCount(0);
        saga.setCreatedAt(now);
        saga.setUpdatedAt(now);
        sagaRepository.save(saga);
        outboxWriter.salesOrderCreated(order);
        return order.getId();
    }

    private static void validatePriceSnapshot(CreateOrderRequest request) {
        if ((request.unitPrice() == null) != (request.currencyCode() == null || request.currencyCode().isBlank())) {
            throw new SalesException(
                    "INVALID_PRICE_SNAPSHOT",
                    "unitPrice and currencyCode must be provided together",
                    400
            );
        }
    }

    private static String normalizeCurrency(String currencyCode) {
        return currencyCode == null ? null : currencyCode.trim().toUpperCase(Locale.ROOT);
    }

    @Transactional
    public void recordReservation(UUID orderId, UUID reservationId) {
        SalesOrderEntity order = lockedOrder(orderId);
        OrderSagaEntity saga = lockedSaga(orderId);
        if (order.getStatus() == OrderStatus.CONFIRMED) {
            return;
        }
        order.setReservationId(reservationId);
        order.setStatus(OrderStatus.STOCK_RESERVED);
        order.setUpdatedAt(Instant.now());
        saga.setCurrentStep("CONFIRM_INVENTORY");
        saga.setUpdatedAt(order.getUpdatedAt());
    }

    @Transactional
    public void confirm(UUID orderId, UUID reservationId, boolean reconciliation) {
        SalesOrderEntity order = lockedOrder(orderId);
        OrderSagaEntity saga = lockedSaga(orderId);
        if (order.getStatus() == OrderStatus.CONFIRMED) {
            return;
        }
        if (order.getStatus() == OrderStatus.CANCELLED || order.getStatus() == OrderStatus.OUT_OF_STOCK) {
            throw new SalesException("ORDER_TERMINAL", "Cancelled order cannot be confirmed", 409);
        }
        order.setReservationId(reservationId);
        order.setStatus(OrderStatus.CONFIRMED);
        order.setFailureReason(null);
        order.setUpdatedAt(Instant.now());
        saga.setStatus("COMPLETED");
        saga.setCurrentStep("CONFIRMED");
        saga.setLastError(null);
        if (reconciliation) {
            saga.setRetryCount(saga.getRetryCount() + 1);
        }
        saga.setUpdatedAt(order.getUpdatedAt());
        outboxWriter.salesOrderConfirmed(order);
    }

    @Transactional
    public void failWithoutReservation(UUID orderId, String failureMessage, boolean insufficientStock) {
        SalesOrderEntity order = lockedOrder(orderId);
        OrderSagaEntity saga = lockedSaga(orderId);
        if (isTerminal(order)) {
            return;
        }
        order.setStatus(insufficientStock ? OrderStatus.OUT_OF_STOCK : OrderStatus.CANCELLED);
        order.setFailureReason(failureMessage);
        order.setUpdatedAt(Instant.now());
        saga.setStatus("FAILED");
        saga.setLastError(failureMessage);
        saga.setCurrentStep("COMPENSATED");
        saga.setUpdatedAt(order.getUpdatedAt());
        outboxWriter.salesOrderCancelled(
                order,
                insufficientStock ? "INSUFFICIENT_STOCK" : "INVENTORY_RESERVATION_FAILED"
        );
    }

    @Transactional
    public void cancelAfterRelease(
            UUID orderId,
            UUID reservationId,
            String failureMessage,
            boolean reconciliation
    ) {
        SalesOrderEntity order = lockedOrder(orderId);
        OrderSagaEntity saga = lockedSaga(orderId);
        if (isTerminal(order)) {
            return;
        }
        order.setReservationId(reservationId);
        order.setStatus(OrderStatus.CANCELLED);
        order.setFailureReason(reconciliation ? "reconciled:RELEASE" : failureMessage);
        order.setUpdatedAt(Instant.now());
        saga.setStatus(reconciliation ? "RECONCILED" : "FAILED");
        saga.setLastError(reconciliation ? null : failureMessage);
        saga.setCurrentStep(reconciliation ? "RELEASED" : "COMPENSATED");
        if (reconciliation) {
            saga.setRetryCount(saga.getRetryCount() + 1);
        }
        saga.setUpdatedAt(order.getUpdatedAt());
        outboxWriter.salesOrderCancelled(
                order,
                reconciliation ? "RECONCILED_RELEASE" : "CONFIRMATION_FAILED"
        );
    }

    @Transactional
    public void markCompensationPending(
            UUID orderId,
            UUID reservationId,
            String compensationFailure
    ) {
        SalesOrderEntity order = lockedOrder(orderId);
        OrderSagaEntity saga = lockedSaga(orderId);
        if (isTerminal(order)) {
            return;
        }
        order.setReservationId(reservationId);
        order.setStatus(OrderStatus.STOCK_RESERVED);
        order.setFailureReason(compensationFailure);
        order.setUpdatedAt(Instant.now());
        saga.setStatus("FAILED");
        saga.setLastError(compensationFailure);
        saga.setCurrentStep("COMPENSATION_PENDING");
        saga.setUpdatedAt(order.getUpdatedAt());
    }

    @Transactional
    public void recordReconcileFailure(UUID orderId, String action, String failureMessage) {
        OrderSagaEntity saga = lockedSaga(orderId);
        saga.setLastError("reconcile " + action + " failed: " + failureMessage);
        saga.setRetryCount(saga.getRetryCount() + 1);
        saga.setUpdatedAt(Instant.now());
    }

    @Transactional
    public void markReservationOutcomeUnknown(UUID orderId, String failureMessage) {
        SalesOrderEntity order = lockedOrder(orderId);
        OrderSagaEntity saga = lockedSaga(orderId);
        if (isTerminal(order)) {
            return;
        }
        Instant now = Instant.now();
        order.setStatus(OrderStatus.PENDING_CONFIRMATION);
        order.setFailureReason("reservation outcome unknown: " + failureMessage);
        order.setUpdatedAt(now);
        saga.setStatus("FAILED");
        saga.setLastError(failureMessage);
        saga.setCurrentStep("RESERVATION_OUTCOME_UNKNOWN");
        saga.setRetryCount(saga.getRetryCount() + 1);
        saga.setUpdatedAt(now);
    }

    private SalesOrderEntity lockedOrder(UUID orderId) {
        return orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new SalesException("ORDER_NOT_FOUND", "Order not found", 404));
    }

    private OrderSagaEntity lockedSaga(UUID orderId) {
        return sagaRepository.findBySalesOrderIdForUpdate(orderId)
                .orElseThrow(() -> new SalesException("SAGA_NOT_FOUND", "Saga not found", 500));
    }

    private static boolean isTerminal(SalesOrderEntity order) {
        return order.getStatus() == OrderStatus.CONFIRMED
                || order.getStatus() == OrderStatus.CANCELLED
                || order.getStatus() == OrderStatus.OUT_OF_STOCK;
    }
}
