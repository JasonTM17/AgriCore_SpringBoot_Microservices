package com.agricore.sales.application.service;

import com.agricore.sales.api.request.CreateOrderRequest;
import com.agricore.sales.api.request.CreateCustomerRequest;
import com.agricore.sales.domain.exception.SalesException;
import com.agricore.sales.domain.model.OrderStatus;
import com.agricore.sales.infrastructure.persistence.CustomerJpaRepository;
import com.agricore.sales.infrastructure.persistence.OrderSagaJpaRepository;
import com.agricore.sales.infrastructure.persistence.SalesOrderItemJpaRepository;
import com.agricore.sales.infrastructure.persistence.SalesOrderJpaRepository;
import com.agricore.sales.infrastructure.persistence.entity.OrderSagaEntity;
import com.agricore.sales.infrastructure.persistence.entity.CustomerEntity;
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
    public CustomerEntity createCustomer(CreateCustomerRequest request) {
        String code = request.code().trim().toUpperCase(Locale.ROOT);
        if (customerRepository.existsByCodeIgnoreCase(code)) {
            throw new SalesException("CUSTOMER_EXISTS", "Customer code already exists", 409);
        }
        CustomerEntity customer = new CustomerEntity();
        customer.setId(UUID.randomUUID());
        customer.setFarmId(request.farmId());
        customer.setCode(code);
        customer.setName(request.name().trim());
        customer.setEmail(request.email());
        customer.setCreatedAt(Instant.now());
        return customerRepository.save(customer);
    }

    @Transactional
    public UUID createOrder(CreateOrderRequest request, String orderNumber) {
        CustomerEntity customer = customerRepository.findById(request.customerId())
                .orElseThrow(() -> new SalesException("CUSTOMER_NOT_FOUND", "Customer not found", 404));
        if (!request.farmId().equals(customer.getFarmId())) {
            throw new SalesException(
                    "CUSTOMER_NOT_FOUND",
                    "Customer not found in the requested farm",
                    404
            );
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
        order.setFarmId(request.farmId());
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
        saga.setStatus("PROCESSING");
        saga.setRetryCount(0);
        saga.setExecutionStartedAt(now);
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
        if (isTerminal(order)) {
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
        saga.setExecutionStartedAt(null);
        saga.setNextAttemptAt(null);
        saga.setCompletedAt(order.getUpdatedAt());
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
        String safeFailureMessage = SalesSagaFailureMessage.bounded(failureMessage);
        order.setStatus(insufficientStock ? OrderStatus.OUT_OF_STOCK : OrderStatus.CANCELLED);
        order.setFailureReason(safeFailureMessage);
        order.setUpdatedAt(Instant.now());
        saga.setStatus("FAILED");
        saga.setLastError(safeFailureMessage);
        saga.setCurrentStep("COMPENSATED");
        saga.setExecutionStartedAt(null);
        saga.setNextAttemptAt(null);
        saga.setCompletedAt(order.getUpdatedAt());
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
        String safeFailureMessage = SalesSagaFailureMessage.bounded(failureMessage);
        order.setReservationId(reservationId);
        order.setStatus(OrderStatus.CANCELLED);
        order.setFailureReason(reconciliation ? "reconciled:RELEASE" : safeFailureMessage);
        order.setUpdatedAt(Instant.now());
        saga.setStatus(reconciliation ? "RECONCILED" : "FAILED");
        saga.setLastError(reconciliation ? null : safeFailureMessage);
        saga.setCurrentStep(reconciliation ? "RELEASED" : "COMPENSATED");
        if (reconciliation) {
            saga.setRetryCount(saga.getRetryCount() + 1);
        }
        saga.setExecutionStartedAt(null);
        saga.setNextAttemptAt(null);
        saga.setCompletedAt(order.getUpdatedAt());
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
            String compensationFailure,
            Instant nextAttemptAt
    ) {
        SalesOrderEntity order = lockedOrder(orderId);
        OrderSagaEntity saga = lockedSaga(orderId);
        if (isTerminal(order)) {
            return;
        }
        String safeFailureMessage = SalesSagaFailureMessage.bounded(compensationFailure);
        order.setReservationId(reservationId);
        order.setStatus(OrderStatus.STOCK_RESERVED);
        order.setFailureReason(safeFailureMessage);
        order.setUpdatedAt(Instant.now());
        saga.setStatus("RETRY_SCHEDULED");
        saga.setLastError(safeFailureMessage);
        saga.setCurrentStep("COMPENSATION_PENDING");
        saga.setNextAttemptAt(nextAttemptAt);
        saga.setExecutionStartedAt(null);
        saga.setUpdatedAt(order.getUpdatedAt());
    }

    @Transactional
    public void recordReconcileFailure(
            UUID orderId,
            String action,
            String failureMessage,
            Instant nextAttemptAt
    ) {
        SalesOrderEntity order = lockedOrder(orderId);
        OrderSagaEntity saga = lockedSaga(orderId);
        if (isTerminal(order) || isTerminalSaga(saga)) {
            return;
        }
        saga.setLastError(SalesSagaFailureMessage.bounded(
                "reconcile " + action + " failed: " + failureMessage
        ));
        saga.setRetryCount(saga.getRetryCount() + 1);
        saga.setStatus("RETRY_SCHEDULED");
        saga.setNextAttemptAt(nextAttemptAt);
        saga.setExecutionStartedAt(null);
        saga.setUpdatedAt(Instant.now());
    }

    @Transactional
    public void markReservationOutcomeUnknown(
            UUID orderId,
            String failureMessage,
            Instant nextAttemptAt
    ) {
        SalesOrderEntity order = lockedOrder(orderId);
        OrderSagaEntity saga = lockedSaga(orderId);
        if (isTerminal(order)) {
            return;
        }
        Instant now = Instant.now();
        String safeFailureMessage = SalesSagaFailureMessage.bounded(failureMessage);
        order.setStatus(OrderStatus.PENDING_CONFIRMATION);
        order.setFailureReason(SalesSagaFailureMessage.bounded(
                "reservation outcome unknown: " + safeFailureMessage
        ));
        order.setUpdatedAt(now);
        saga.setStatus("RETRY_SCHEDULED");
        saga.setLastError(safeFailureMessage);
        saga.setCurrentStep("RESERVATION_OUTCOME_UNKNOWN");
        saga.setRetryCount(saga.getRetryCount() + 1);
        saga.setNextAttemptAt(nextAttemptAt);
        saga.setExecutionStartedAt(null);
        saga.setUpdatedAt(now);
    }

    @Transactional
    public void markConfirmationRetry(
            UUID orderId,
            UUID reservationId,
            String failureMessage,
            Instant nextAttemptAt
    ) {
        SalesOrderEntity order = lockedOrder(orderId);
        OrderSagaEntity saga = lockedSaga(orderId);
        if (isTerminal(order)) {
            return;
        }
        String safeFailureMessage = SalesSagaFailureMessage.bounded(failureMessage);
        order.setReservationId(reservationId);
        order.setStatus(OrderStatus.STOCK_RESERVED);
        order.setFailureReason(safeFailureMessage);
        order.setUpdatedAt(Instant.now());
        saga.setStatus("RETRY_SCHEDULED");
        saga.setLastError(safeFailureMessage);
        saga.setCurrentStep("CONFIRM_INVENTORY");
        saga.setRetryCount(saga.getRetryCount() + 1);
        saga.setNextAttemptAt(nextAttemptAt);
        saga.setExecutionStartedAt(null);
        saga.setUpdatedAt(order.getUpdatedAt());
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

    private static boolean isTerminalSaga(OrderSagaEntity saga) {
        return "COMPLETED".equals(saga.getStatus())
                || "FAILED".equals(saga.getStatus())
                || "RECONCILED".equals(saga.getStatus())
                || "TIMED_OUT".equals(saga.getStatus());
    }
}
