package com.agricore.sales.application.service;

import com.agricore.sales.domain.exception.SalesException;
import com.agricore.sales.infrastructure.persistence.OrderSagaJpaRepository;
import com.agricore.sales.infrastructure.persistence.SalesOrderJpaRepository;
import com.agricore.sales.infrastructure.persistence.entity.OrderSagaEntity;
import com.agricore.sales.infrastructure.persistence.entity.SalesOrderEntity;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class SalesSagaRecoveryStateService {

    private final OrderSagaJpaRepository sagaRepository;
    private final SalesOrderJpaRepository orderRepository;

    public SalesSagaRecoveryStateService(
            OrderSagaJpaRepository sagaRepository,
            SalesOrderJpaRepository orderRepository
    ) {
        this.sagaRepository = sagaRepository;
        this.orderRepository = orderRepository;
    }

    @Transactional(readOnly = true)
    public List<UUID> findRecoverableOrderIds(
            Instant now,
            Instant staleBefore,
            int batchSize
    ) {
        return sagaRepository.findRecoverableOrderIds(
                now,
                staleBefore,
                PageRequest.of(0, batchSize)
        );
    }

    @Transactional
    public Optional<RecoveryClaim> claim(
            UUID orderId,
            Instant now,
            Instant staleBefore,
            int maxAttempts
    ) {
        SalesOrderEntity order = lockedOrder(orderId);
        OrderSagaEntity saga = lockedSaga(orderId);
        if (!isRecoverableNow(saga, now, staleBefore)) {
            return Optional.empty();
        }
        if (saga.getRetryCount() >= maxAttempts) {
            markTimedOut(order, saga, "Saga recovery attempt limit reached", now);
            return Optional.empty();
        }

        int attempt = saga.getRetryCount() + 1;
        saga.setRetryCount(attempt);
        saga.setStatus("PROCESSING");
        saga.setExecutionStartedAt(now);
        saga.setNextAttemptAt(null);
        saga.setUpdatedAt(now);
        return Optional.of(new RecoveryClaim(
                orderId,
                saga.getCurrentStep(),
                attempt
        ));
    }

    @Transactional
    public void scheduleRetry(UUID orderId, String failureMessage, Instant nextAttemptAt) {
        SalesOrderEntity order = lockedOrder(orderId);
        OrderSagaEntity saga = lockedSaga(orderId);
        if (isTerminal(saga.getStatus())) {
            return;
        }
        Instant now = Instant.now();
        order.setFailureReason(bounded(failureMessage));
        order.setUpdatedAt(now);
        saga.setStatus("RETRY_SCHEDULED");
        saga.setLastError(bounded(failureMessage));
        saga.setExecutionStartedAt(null);
        saga.setNextAttemptAt(nextAttemptAt);
        saga.setUpdatedAt(now);
    }

    @Transactional
    public void markTimedOut(UUID orderId, String failureMessage) {
        SalesOrderEntity order = lockedOrder(orderId);
        OrderSagaEntity saga = lockedSaga(orderId);
        if (isTerminal(saga.getStatus())) {
            return;
        }
        markTimedOut(order, saga, failureMessage, Instant.now());
    }

    private void markTimedOut(
            SalesOrderEntity order,
            OrderSagaEntity saga,
            String failureMessage,
            Instant now
    ) {
        String message = bounded(failureMessage);
        order.setFailureReason(message);
        order.setUpdatedAt(now);
        saga.setStatus("TIMED_OUT");
        saga.setCurrentStep("MANUAL_RECONCILIATION_REQUIRED");
        saga.setLastError(message);
        saga.setExecutionStartedAt(null);
        saga.setNextAttemptAt(null);
        saga.setCompletedAt(now);
        saga.setUpdatedAt(now);
    }

    private static boolean isRecoverableNow(
            OrderSagaEntity saga,
            Instant now,
            Instant staleBefore
    ) {
        if ("RETRY_SCHEDULED".equals(saga.getStatus())) {
            return saga.getNextAttemptAt() != null && !saga.getNextAttemptAt().isAfter(now);
        }
        return "PROCESSING".equals(saga.getStatus())
                && saga.getExecutionStartedAt() != null
                && saga.getExecutionStartedAt().isBefore(staleBefore);
    }

    private SalesOrderEntity lockedOrder(UUID orderId) {
        return orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new SalesException("ORDER_NOT_FOUND", "Order not found", 404));
    }

    private OrderSagaEntity lockedSaga(UUID orderId) {
        return sagaRepository.findBySalesOrderIdForUpdate(orderId)
                .orElseThrow(() -> new SalesException("SAGA_NOT_FOUND", "Saga not found", 500));
    }

    private static boolean isTerminal(String status) {
        return "COMPLETED".equals(status)
                || "FAILED".equals(status)
                || "RECONCILED".equals(status)
                || "TIMED_OUT".equals(status);
    }

    private static String bounded(String value) {
        String normalized = value == null || value.isBlank() ? "Saga recovery failed" : value.trim();
        return normalized.length() <= 1_000 ? normalized : normalized.substring(0, 1_000);
    }

    public record RecoveryClaim(UUID orderId, String step, int attempt) {
    }
}
