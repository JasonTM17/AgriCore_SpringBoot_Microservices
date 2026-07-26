package com.agricore.sales.application.service;

import com.agricore.sales.infrastructure.client.InventoryClient;
import com.agricore.sales.infrastructure.persistence.SalesOrderJpaRepository;
import com.agricore.sales.infrastructure.persistence.entity.SalesOrderEntity;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class SalesSagaRecoveryService {

    private final SalesOrderJpaRepository orderRepository;
    private final InventoryClient inventoryClient;
    private final SalesOrderTransactionService transactions;
    private final SalesSagaRecoveryStateService stateService;
    private final SalesSagaRecoveryPolicy policy;

    public SalesSagaRecoveryService(
            SalesOrderJpaRepository orderRepository,
            InventoryClient inventoryClient,
            SalesOrderTransactionService transactions,
            SalesSagaRecoveryStateService stateService,
            SalesSagaRecoveryPolicy policy
    ) {
        this.orderRepository = orderRepository;
        this.inventoryClient = inventoryClient;
        this.transactions = transactions;
        this.stateService = stateService;
        this.policy = policy;
    }

    public void recover(UUID orderId, Instant now) {
        Optional<SalesSagaRecoveryStateService.RecoveryClaim> claim =
                stateService.claim(
                        orderId,
                        now,
                        policy.staleBefore(now),
                        policy.maxAttempts()
                );
        if (claim.isEmpty()) {
            return;
        }

        SalesSagaRecoveryStateService.RecoveryClaim recoveryClaim = claim.get();
        try {
            switch (recoveryClaim.step()) {
                case "RESERVE_INVENTORY", "RESERVATION_OUTCOME_UNKNOWN" ->
                        recoverReservation(orderId);
                case "CONFIRM_INVENTORY" -> recoverConfirmation(orderId);
                case "COMPENSATION_PENDING" -> recoverCompensation(orderId);
                default -> stateService.markTimedOut(
                        orderId,
                        "Unsupported saga recovery step: " + recoveryClaim.step()
                );
            }
        } catch (InventoryClient.InventoryReservationException failure) {
            if (failure.isInsufficientStock()) {
                transactions.failWithoutReservation(orderId, failureMessage(failure), true);
            } else {
                handleFailure(recoveryClaim, failureMessage(failure));
            }
        } catch (RuntimeException failure) {
            handleFailure(recoveryClaim, failureMessage(failure));
        }
    }

    private void recoverReservation(UUID orderId) {
        SalesOrderEntity order = reloadOrder(orderId);
        Optional<InventoryClient.ReservationState> existing =
                inventoryClient.findByReference(order.getFarmId(), "SalesOrder", orderId.toString());
        if (existing.isEmpty()) {
            UUID reservationId = inventoryClient.reserve(
                    order.getFarmId(),
                    order.getInventoryItemId(),
                    order.getQuantity(),
                    orderId.toString()
            );
            transactions.recordReservation(orderId, reservationId);
            inventoryClient.confirm(order.getFarmId(), reservationId);
            transactions.confirm(orderId, reservationId, false);
            return;
        }

        InventoryClient.ReservationState reservation = existing.get();
        validateReservation(order, reservation);
        switch (reservation.status()) {
            case "FULFILLED" -> transactions.confirm(orderId, reservation.id(), false);
            case "RELEASED" -> transactions.cancelAfterRelease(
                    orderId,
                    reservation.id(),
                    "Inventory reservation was already released during recovery",
                    false
            );
            case "ACTIVE" -> {
                transactions.recordReservation(orderId, reservation.id());
                inventoryClient.confirm(order.getFarmId(), reservation.id());
                transactions.confirm(orderId, reservation.id(), false);
            }
            default -> throw new IllegalStateException(
                    "Unsupported inventory reservation status: " + reservation.status()
            );
        }
    }

    private void recoverConfirmation(UUID orderId) {
        SalesOrderEntity order = reloadOrder(orderId);
        UUID reservationId = requiredReservation(order);
        inventoryClient.confirm(order.getFarmId(), reservationId);
        transactions.confirm(orderId, reservationId, false);
    }

    private void recoverCompensation(UUID orderId) {
        SalesOrderEntity order = reloadOrder(orderId);
        UUID reservationId = requiredReservation(order);
        InventoryClient.ReleaseOutcome outcome = inventoryClient.release(order.getFarmId(), reservationId);
        if (outcome == InventoryClient.ReleaseOutcome.FULFILLED) {
            transactions.confirm(orderId, reservationId, false);
        } else {
            transactions.cancelAfterRelease(
                    orderId,
                    reservationId,
                    "Inventory reservation released during recovery",
                    false
            );
        }
    }

    private void handleFailure(
            SalesSagaRecoveryStateService.RecoveryClaim claim,
            String failureMessage
    ) {
        if (claim.attempt() < policy.maxAttempts()) {
            stateService.scheduleRetry(
                    claim.orderId(),
                    failureMessage,
                    policy.nextAttemptAt(claim.attempt(), Instant.now())
            );
            return;
        }
        compensateAfterExhaustion(claim.orderId(), failureMessage);
    }

    private void compensateAfterExhaustion(UUID orderId, String failureMessage) {
        SalesOrderEntity order = reloadOrder(orderId);
        try {
            if (order.getReservationId() == null) {
                Optional<InventoryClient.ReservationState> existing =
                        inventoryClient.findByReference(
                                order.getFarmId(),
                                "SalesOrder",
                                orderId.toString()
                        );
                if (existing.isEmpty()) {
                    transactions.failWithoutReservation(orderId, failureMessage, false);
                    return;
                }
                validateReservation(order, existing.get());
                switch (existing.get().status()) {
                    case "FULFILLED" -> transactions.confirm(orderId, existing.get().id(), false);
                    case "RELEASED" -> transactions.cancelAfterRelease(
                            orderId,
                            existing.get().id(),
                            "Inventory reservation was released after recovery exhaustion",
                            false
                    );
                    case "ACTIVE" -> releaseOrConfirm(order, existing.get().id());
                    default -> stateService.markTimedOut(
                            orderId,
                            failureMessage + "; unsupported reservation status " + existing.get().status()
                    );
                }
                return;
            }
            releaseOrConfirm(order, order.getReservationId());
        } catch (RuntimeException compensationFailure) {
            stateService.markTimedOut(
                    orderId,
                    failureMessage + "; compensation failed: " + failureMessage(compensationFailure)
            );
        }
    }

    private void releaseOrConfirm(SalesOrderEntity order, UUID reservationId) {
        UUID orderId = order.getId();
        InventoryClient.ReleaseOutcome outcome =
                inventoryClient.release(order.getFarmId(), reservationId);
        if (outcome == InventoryClient.ReleaseOutcome.FULFILLED) {
            transactions.confirm(orderId, reservationId, false);
        } else {
            transactions.cancelAfterRelease(
                    orderId,
                    reservationId,
                    "Inventory reservation released after recovery exhaustion",
                    false
            );
        }
    }

    private SalesOrderEntity reloadOrder(UUID orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Sales order not found: " + orderId));
    }

    private static UUID requiredReservation(SalesOrderEntity order) {
        if (order.getReservationId() == null) {
            throw new IllegalStateException("Order has no reservation for saga recovery");
        }
        return order.getReservationId();
    }

    private static void validateReservation(
            SalesOrderEntity order,
            InventoryClient.ReservationState reservation
    ) {
        if (!order.getInventoryItemId().equals(reservation.inventoryItemId())
                || order.getQuantity().compareTo(reservation.quantity()) != 0) {
            throw new IllegalStateException("Inventory reservation does not match the sales order");
        }
    }

    private static String failureMessage(Exception failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank()
                ? "Inventory saga recovery failed"
                : message;
    }
}
