package com.agricore.sales.application.service;

import com.agricore.sales.api.request.CreateCustomerRequest;
import com.agricore.sales.api.request.CreateOrderRequest;
import com.agricore.sales.api.response.SalesOrderItemResponse;
import com.agricore.sales.api.response.SalesOrderResponse;
import com.agricore.sales.domain.exception.SalesException;
import com.agricore.sales.domain.model.OrderStatus;
import com.agricore.sales.infrastructure.client.InventoryClient;
import com.agricore.sales.infrastructure.persistence.OrderSagaJpaRepository;
import com.agricore.sales.infrastructure.persistence.SalesOrderItemJpaRepository;
import com.agricore.sales.infrastructure.persistence.SalesOrderJpaRepository;
import com.agricore.sales.infrastructure.persistence.entity.CustomerEntity;
import com.agricore.sales.infrastructure.persistence.entity.OrderSagaEntity;
import com.agricore.sales.infrastructure.persistence.entity.SalesOrderEntity;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

/**
 * Orchestrates inventory calls without holding a database transaction across service boundaries.
 * Every local transition delegates to {@link SalesOrderTransactionService}, which persists state
 * and its lifecycle event in one short transaction.
 */
@Service
public class SalesSagaService {

    private final SalesOrderJpaRepository orderRepository;
    private final OrderSagaJpaRepository sagaRepository;
    private final SalesOrderItemJpaRepository itemRepository;
    private final InventoryClient inventoryClient;
    private final SalesOrderTransactionService transactions;
    private final SalesMetrics metrics;
    private final SalesSagaRecoveryPolicy recoveryPolicy;
    private final SalesAccessGuard accessGuard;

    public SalesSagaService(
            SalesOrderJpaRepository orderRepository,
            OrderSagaJpaRepository sagaRepository,
            SalesOrderItemJpaRepository itemRepository,
            InventoryClient inventoryClient,
            SalesOrderTransactionService transactions,
            SalesMetrics metrics,
            SalesSagaRecoveryPolicy recoveryPolicy,
            SalesAccessGuard accessGuard
    ) {
        this.orderRepository = orderRepository;
        this.sagaRepository = sagaRepository;
        this.itemRepository = itemRepository;
        this.inventoryClient = inventoryClient;
        this.transactions = transactions;
        this.metrics = metrics;
        this.recoveryPolicy = recoveryPolicy;
        this.accessGuard = accessGuard;
    }

    public CustomerEntity createCustomer(CreateCustomerRequest request) {
        accessGuard.requireFarm(request.farmId());
        try {
            return transactions.createCustomer(request);
        } catch (DataIntegrityViolationException exception) {
            throw new SalesException("CUSTOMER_EXISTS", "Customer code already exists", 409);
        }
    }

    public SalesOrderResponse placeOrder(CreateOrderRequest request) {
        accessGuard.requireFarm(request.farmId());
        UUID orderId = createOrder(request);
        UUID reservationId = null;
        try {
            SalesOrderEntity order = reloadOrder(orderId);
            reservationId = inventoryClient.reserve(
                    order.getFarmId(),
                    order.getInventoryItemId(),
                    order.getQuantity(),
                    order.getId().toString()
            );
            transactions.recordReservation(orderId, reservationId);
            inventoryClient.confirm(order.getFarmId(), reservationId);
            transactions.confirm(orderId, reservationId, false);
        } catch (Exception failure) {
            compensate(orderId, reservationId, failure);
        }

        SalesOrderResponse response = get(orderId);
        metrics.recordSagaOutcome(response.sagaStatus());
        return response;
    }

    private UUID createOrder(CreateOrderRequest request) {
        try {
            return transactions.createOrder(request, request.orderNumber().trim().toUpperCase());
        } catch (DataIntegrityViolationException exception) {
            throw new SalesException("ORDER_EXISTS", "Order number already exists", 409);
        }
    }

    private void compensate(UUID orderId, UUID reservationId, Exception failure) {
        String failureMessage = failureMessage(failure);
        if (reservationId == null) {
            if (shouldResolveReservation(failure)) {
                try {
                    SalesOrderEntity order = reloadOrder(orderId);
                    var reservation = inventoryClient.findByReference(
                            order.getFarmId(),
                            "SalesOrder",
                            orderId.toString()
                    );
                    if (reservation.isPresent()) {
                        compensateKnownReservation(orderId, reservation.get(), failureMessage);
                        return;
                    }
                    transactions.markReservationOutcomeUnknown(
                            orderId,
                            failureMessage + "; reservation is not visible yet",
                            recoveryPolicy.nextAttemptAt(1, Instant.now())
                    );
                    return;
                } catch (Exception lookupFailure) {
                    transactions.markReservationOutcomeUnknown(
                            orderId,
                            failureMessage + "; reservation lookup failed: " + failureMessage(lookupFailure),
                            recoveryPolicy.nextAttemptAt(1, Instant.now())
                    );
                    return;
                }
            }
            boolean insufficientStock = failure instanceof InventoryClient.InventoryReservationException inventoryFailure
                    && inventoryFailure.isInsufficientStock();
            transactions.failWithoutReservation(orderId, failureMessage, insufficientStock);
            return;
        }

        transactions.markConfirmationRetry(
                orderId,
                reservationId,
                failureMessage,
                recoveryPolicy.nextAttemptAt(1, Instant.now())
        );
    }

    private void compensateKnownReservation(
            UUID orderId,
            InventoryClient.ReservationState reservation,
            String failureMessage
    ) {
        SalesOrderEntity order = reloadOrder(orderId);
        requireMatchingReservation(order, reservation);
        UUID reservationId = reservation.id();
        switch (reservation.status()) {
            case "FULFILLED" -> transactions.confirm(orderId, reservationId, false);
            case "RELEASED" -> transactions.cancelAfterRelease(orderId, reservationId, failureMessage, false);
            case "ACTIVE" -> {
                transactions.recordReservation(orderId, reservationId);
                transactions.markConfirmationRetry(
                        orderId,
                        reservationId,
                        failureMessage,
                        recoveryPolicy.nextAttemptAt(1, Instant.now())
                );
            }
            default -> transactions.markReservationOutcomeUnknown(
                    orderId,
                    failureMessage + "; unsupported reservation status " + reservation.status(),
                    recoveryPolicy.nextAttemptAt(1, Instant.now())
            );
        }
    }

    private static boolean shouldResolveReservation(Exception failure) {
        if (!(failure instanceof InventoryClient.InventoryReservationException inventoryFailure)) {
            return true;
        }
        // A 409 is an authoritative reserve decision. Transport/5xx failures
        // may have committed a hold before the response was lost.
        return !inventoryFailure.isInsufficientStock() && inventoryFailure.getStatus() != 409;
    }

    private static void requireMatchingReservation(
            SalesOrderEntity order,
            InventoryClient.ReservationState reservation
    ) {
        if (!order.getInventoryItemId().equals(reservation.inventoryItemId())
                || order.getQuantity().compareTo(reservation.quantity()) != 0) {
            throw new SalesException(
                    "RESERVATION_SCOPE_MISMATCH",
                    "Inventory reservation does not match the sales order",
                    409
            );
        }
    }

    public SalesOrderResponse get(UUID orderId) {
        SalesOrderEntity order = reloadOrder(orderId);
        accessGuard.requireFarm(order.getFarmId());
        OrderSagaEntity saga = sagaRepository.findBySalesOrderId(orderId).orElse(null);
        return toResponse(order, saga);
    }

    public SalesOrderResponse reconcile(UUID orderId, String action) {
        String normalizedAction = normalizeReconcileAction(action);
        SalesOrderEntity order = reloadOrder(orderId);
        accessGuard.requireFarm(order.getFarmId());
        if (isAlreadyReconciled(order, normalizedAction)) {
            return get(orderId);
        }
        requireReconciliationAllowed(order, normalizedAction);
        if (order.getReservationId() == null) {
            order = resolveUnknownReservation(orderId, order, normalizedAction);
        }
        if (order.getReservationId() == null) {
            throw new SalesException("NO_RESERVATION", "Order has no inventory reservation to reconcile", 409);
        }

        try {
            if ("RELEASE".equals(normalizedAction)) {
                reconcileRelease(order);
            } else {
                inventoryClient.confirm(order.getFarmId(), order.getReservationId());
                transactions.confirm(orderId, order.getReservationId(), true);
            }
        } catch (SalesException exception) {
            throw exception;
        } catch (Exception failure) {
            String message = failureMessage(failure);
            transactions.recordReconcileFailure(
                    orderId,
                    normalizedAction,
                    message,
                    recoveryPolicy.nextAttemptAt(1, Instant.now())
            );
            throw new SalesException("RECONCILE_FAILED", message, 502);
        }
        return get(orderId);
    }

    private SalesOrderEntity resolveUnknownReservation(
            UUID orderId,
            SalesOrderEntity order,
            String action
    ) {
        OrderSagaEntity saga = sagaRepository.findBySalesOrderId(orderId).orElse(null);
        if (saga == null || !"RESERVATION_OUTCOME_UNKNOWN".equals(saga.getCurrentStep())) {
            return order;
        }
        try {
            var reservation = inventoryClient.findByReference(
                    order.getFarmId(),
                    "SalesOrder",
                    orderId.toString()
            );
            if (reservation.isEmpty()) {
                transactions.recordReconcileFailure(
                        orderId,
                        action,
                        "Inventory has not exposed a reservation for the order reference",
                        recoveryPolicy.nextAttemptAt(1, Instant.now())
                );
                throw new SalesException(
                        "RESERVATION_OUTCOME_UNKNOWN",
                        "Inventory reservation outcome is still unknown; retry reconciliation",
                        409
                );
            }
            InventoryClient.ReservationState state = reservation.get();
            requireMatchingReservation(order, state);
            if ("FULFILLED".equals(state.status())) {
                transactions.confirm(orderId, state.id(), true);
            } else if ("RELEASED".equals(state.status())) {
                transactions.cancelAfterRelease(orderId, state.id(), "reconciled:RELEASE", true);
            } else {
                transactions.recordReservation(orderId, state.id());
            }
            return reloadOrder(orderId);
        } catch (SalesException exception) {
            throw exception;
        } catch (Exception failure) {
            String message = failureMessage(failure);
            transactions.recordReconcileFailure(
                    orderId,
                    action,
                    message,
                    recoveryPolicy.nextAttemptAt(1, Instant.now())
            );
            throw new SalesException("RECONCILE_FAILED", message, 502);
        }
    }

    private void reconcileRelease(SalesOrderEntity order) {
        InventoryClient.ReleaseOutcome outcome = inventoryClient.release(
                order.getFarmId(),
                order.getReservationId()
        );
        if (outcome == InventoryClient.ReleaseOutcome.FULFILLED) {
            transactions.confirm(order.getId(), order.getReservationId(), true);
        } else {
            transactions.cancelAfterRelease(
                    order.getId(),
                    order.getReservationId(),
                    "reconciled:RELEASE",
                    true
            );
        }
    }

    private String normalizeReconcileAction(String action) {
        if (action == null || action.isBlank()) {
            throw new SalesException("INVALID_ACTION", "action is required: RELEASE or CONFIRM", 400);
        }
        String normalized = action.trim().toUpperCase();
        if (!"RELEASE".equals(normalized) && !"CONFIRM".equals(normalized)) {
            throw new SalesException("INVALID_ACTION", "action must be RELEASE or CONFIRM", 400);
        }
        return normalized;
    }

    private boolean isAlreadyReconciled(SalesOrderEntity order, String action) {
        if (order.getStatus() == OrderStatus.CONFIRMED && "CONFIRM".equals(action)) {
            return true;
        }
        return order.getStatus() == OrderStatus.CANCELLED
                && "RELEASE".equals(action);
    }

    private static void requireReconciliationAllowed(SalesOrderEntity order, String action) {
        if (order.getStatus() == OrderStatus.CONFIRMED
                || order.getStatus() == OrderStatus.CANCELLED
                || order.getStatus() == OrderStatus.OUT_OF_STOCK) {
            throw new SalesException(
                    "ORDER_TERMINAL",
                    "Action " + action + " is incompatible with terminal order status " + order.getStatus(),
                    409
            );
        }
    }

    private SalesOrderEntity reloadOrder(UUID orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new SalesException("ORDER_NOT_FOUND", "Order not found", 404));
    }

    private static String failureMessage(Exception failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank() ? "Inventory saga failed" : message;
    }

    private SalesOrderResponse toResponse(SalesOrderEntity order, OrderSagaEntity saga) {
        return new SalesOrderResponse(
                order.getId(), order.getOrderNumber(), order.getFarmId(), order.getCustomerId(),
                order.getStatus().name(),
                order.getInventoryItemId(), order.getQuantity(), order.getReservationId(), order.getCorrelationId(),
                order.getFailureReason(),
                saga == null ? null : saga.getStatus(),
                saga == null ? null : saga.getCurrentStep(),
                saga == null ? null : saga.getRetryCount(),
                saga == null ? null : saga.getNextAttemptAt(),
                saga == null ? null : saga.getCompletedAt(),
                order.getCreatedAt(),
                order.getCurrencyCode(),
                order.getSubtotalAmount(),
                order.getTotalAmount(),
                itemRepository.findAllBySalesOrderIdOrderByLineNumber(order.getId()).stream()
                        .map(item -> new SalesOrderItemResponse(
                                item.getLineNumber(),
                                item.getInventoryItemId(),
                                item.getQuantity(),
                                item.getUnitPrice(),
                                item.getLineTotal(),
                                item.getCurrencyCode()
                        ))
                        .toList()
        );
    }
}
