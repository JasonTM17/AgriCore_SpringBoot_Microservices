package com.agricore.sales.application.service;

import com.agricore.sales.api.request.CreateCustomerRequest;
import com.agricore.sales.api.request.CreateOrderRequest;
import com.agricore.sales.api.response.SalesOrderResponse;
import com.agricore.sales.domain.exception.SalesException;
import com.agricore.sales.domain.model.OrderStatus;
import com.agricore.sales.infrastructure.client.InventoryClient;
import com.agricore.sales.infrastructure.persistence.CustomerJpaRepository;
import com.agricore.sales.infrastructure.persistence.OrderSagaJpaRepository;
import com.agricore.sales.infrastructure.persistence.SalesOrderJpaRepository;
import com.agricore.sales.infrastructure.persistence.entity.CustomerEntity;
import com.agricore.sales.infrastructure.persistence.entity.OrderSagaEntity;
import com.agricore.sales.infrastructure.persistence.entity.SalesOrderEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Orchestration saga: CreateOrder → ReserveInventory → ConfirmInventory → Confirm order.
 * On reserve failure: mark OUT_OF_STOCK / CANCELLED and persist saga failure (compensation is no-op if no reservation).
 * On post-reserve failure: release inventory then cancel.
 * Confirm commits stock (on-hand + reserved decrement); without it reserved stock would stay held forever.
 */
@Service
public class SalesSagaService {

    private final CustomerJpaRepository customerRepository;
    private final SalesOrderJpaRepository orderRepository;
    private final OrderSagaJpaRepository sagaRepository;
    private final InventoryClient inventoryClient;

    public SalesSagaService(
            CustomerJpaRepository customerRepository,
            SalesOrderJpaRepository orderRepository,
            OrderSagaJpaRepository sagaRepository,
            InventoryClient inventoryClient
    ) {
        this.customerRepository = customerRepository;
        this.orderRepository = orderRepository;
        this.sagaRepository = sagaRepository;
        this.inventoryClient = inventoryClient;
    }

    @Transactional
    public CustomerEntity createCustomer(CreateCustomerRequest request) {
        String code = request.code().trim().toUpperCase();
        if (customerRepository.existsByCodeIgnoreCase(code)) {
            throw new SalesException("CUSTOMER_EXISTS", "Customer code already exists", 409);
        }
        CustomerEntity c = new CustomerEntity();
        c.setId(UUID.randomUUID());
        c.setCode(code);
        c.setName(request.name().trim());
        c.setEmail(request.email());
        c.setCreatedAt(Instant.now());
        return customerRepository.save(c);
    }

    /**
     * Creates order and runs reservation saga step. Returns terminal outcome.
     */
    public SalesOrderResponse placeOrder(CreateOrderRequest request) {
        if (!customerRepository.existsById(request.customerId())) {
            throw new SalesException("CUSTOMER_NOT_FOUND", "Customer not found", 404);
        }
        String orderNumber = request.orderNumber().trim().toUpperCase();
        if (orderRepository.existsByOrderNumberIgnoreCase(orderNumber)) {
            throw new SalesException("ORDER_EXISTS", "Order number already exists", 409);
        }

        Instant now = Instant.now();
        UUID correlationId = UUID.randomUUID();

        SalesOrderEntity order = new SalesOrderEntity();
        order.setId(UUID.randomUUID());
        order.setOrderNumber(orderNumber);
        order.setCustomerId(request.customerId());
        order.setStatus(OrderStatus.PENDING_CONFIRMATION);
        order.setInventoryItemId(request.inventoryItemId());
        order.setQuantity(request.quantity());
        order.setCorrelationId(correlationId);
        order.setCreatedAt(now);
        order.setUpdatedAt(now);
        order = orderRepository.saveAndFlush(order);

        OrderSagaEntity saga = new OrderSagaEntity();
        saga.setId(UUID.randomUUID());
        saga.setSalesOrderId(order.getId());
        saga.setCorrelationId(correlationId);
        saga.setCurrentStep("RESERVE_INVENTORY");
        saga.setStatus("RUNNING");
        saga.setRetryCount(0);
        saga.setCreatedAt(now);
        saga.setUpdatedAt(now);
        saga = sagaRepository.saveAndFlush(saga);

        // Held outside the try so both catch blocks can compensate a reservation that inventory
        // already committed. Reading it back off the order row instead would miss the window
        // between the remote reserve succeeding and the local save persisting the id.
        UUID reservationId = null;
        try {
            reservationId = inventoryClient.reserve(
                    order.getInventoryItemId(),
                    order.getQuantity(),
                    order.getId().toString()
            );
            order = reloadOrder(order.getId());
            order.setReservationId(reservationId);
            order.setStatus(OrderStatus.STOCK_RESERVED);
            order.setUpdatedAt(Instant.now());
            order = orderRepository.saveAndFlush(order);

            saga = reloadSaga(order.getId());
            saga.setCurrentStep("CONFIRM_INVENTORY");
            saga.setUpdatedAt(Instant.now());
            saga = sagaRepository.saveAndFlush(saga);

            inventoryClient.confirm(reservationId);

            order = reloadOrder(order.getId());
            order.setStatus(OrderStatus.CONFIRMED);
            order.setUpdatedAt(Instant.now());
            order = orderRepository.saveAndFlush(order);

            saga = reloadSaga(order.getId());
            saga.setCurrentStep("CONFIRMED");
            saga.setStatus("COMPLETED");
            saga.setUpdatedAt(Instant.now());
            saga = sagaRepository.saveAndFlush(saga);
        } catch (InventoryClient.InventoryReservationException ex) {
            // A failure at the confirm step leaves a reservation inventory already committed.
            // Without this release the stock stays held forever while the saga below records
            // itself as COMPENSATED, and reconcile refuses the order for having no reservation.
            String releaseError = releaseQuietly(reservationId);

            order = reloadOrder(order.getId());
            if (ex.isInsufficientStock()) {
                order.setStatus(OrderStatus.OUT_OF_STOCK);
            } else {
                order.setStatus(OrderStatus.CANCELLED);
            }
            order.setFailureReason(ex.getMessage());
            order.setUpdatedAt(Instant.now());
            order = orderRepository.saveAndFlush(order);

            saga = reloadSaga(order.getId());
            saga.setStatus("FAILED");
            saga.setLastError(releaseError == null ? ex.getMessage() : ex.getMessage() + " | " + releaseError);
            saga.setCurrentStep("COMPENSATED");
            saga.setUpdatedAt(Instant.now());
            saga = sagaRepository.saveAndFlush(saga);
        } catch (Exception ex) {
            // Compensation: release if we already reserved
            String releaseError = releaseQuietly(reservationId);
            if (releaseError != null) {
                saga = reloadSaga(order.getId());
                saga.setLastError(releaseError);
                sagaRepository.saveAndFlush(saga);
            }
            order = reloadOrder(order.getId());
            order.setStatus(OrderStatus.CANCELLED);
            order.setFailureReason(ex.getMessage());
            order.setUpdatedAt(Instant.now());
            order = orderRepository.saveAndFlush(order);
            saga = reloadSaga(order.getId());
            saga.setStatus("FAILED");
            saga.setCurrentStep("COMPENSATED");
            saga.setUpdatedAt(Instant.now());
            saga = sagaRepository.saveAndFlush(saga);
        }

        return toResponse(order, saga);
    }

    /**
     * Releases a reservation during compensation, returning a description of the failure instead of
     * throwing. Compensation runs while another failure is already being handled, so letting this
     * throw would replace the original cause with a secondary one and abandon the order mid-update.
     *
     * <p>Safe to call for a reservation inventory has already finalised: {@code release} on a
     * terminal reservation is a no-op there, so this cannot double-decrement.
     *
     * @return null when there was nothing to release or the release succeeded
     */
    private String releaseQuietly(UUID reservationId) {
        if (reservationId == null) {
            return null;
        }
        try {
            inventoryClient.release(reservationId);
            return null;
        } catch (Exception releaseEx) {
            return "Release failed: " + releaseEx.getMessage();
        }
    }

    private SalesOrderEntity reloadOrder(UUID id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new SalesException("ORDER_NOT_FOUND", "Order not found", 404));
    }

    private OrderSagaEntity reloadSaga(UUID orderId) {
        return sagaRepository.findBySalesOrderId(orderId)
                .orElseThrow(() -> new SalesException("SAGA_NOT_FOUND", "Saga not found", 500));
    }

    @Transactional(readOnly = true)
    public SalesOrderResponse get(UUID id) {
        SalesOrderEntity order = orderRepository.findById(id)
                .orElseThrow(() -> new SalesException("ORDER_NOT_FOUND", "Order not found", 404));
        OrderSagaEntity saga = sagaRepository.findBySalesOrderId(id).orElse(null);
        return toResponse(order, saga);
    }

    /**
     * Operator recovery for stuck inventory holds after a partial saga.
     * <ul>
     *   <li>{@code RELEASE} — release ACTIVE reservation and cancel order</li>
     *   <li>{@code CONFIRM} — fulfill reservation and mark order CONFIRMED</li>
     * </ul>
     * Applies when order has a non-null reservationId and is not already terminal without hold.
     */
    public SalesOrderResponse reconcile(UUID orderId, String action) {
        if (action == null || action.isBlank()) {
            throw new SalesException("INVALID_ACTION", "action is required: RELEASE or CONFIRM", 400);
        }
        String act = action.trim().toUpperCase();
        SalesOrderEntity order = reloadOrder(orderId);
        if (order.getReservationId() == null) {
            throw new SalesException("NO_RESERVATION", "Order has no inventory reservation to reconcile", 409);
        }
        if (order.getStatus() == OrderStatus.CONFIRMED && "CONFIRM".equals(act)) {
            return get(orderId);
        }
        if (order.getStatus() == OrderStatus.CANCELLED && "RELEASE".equals(act)
                && order.getFailureReason() != null && order.getFailureReason().contains("reconciled:RELEASE")) {
            return get(orderId);
        }

        OrderSagaEntity saga = reloadSaga(orderId);
        try {
            if ("RELEASE".equals(act)) {
                inventoryClient.release(order.getReservationId());
                order = reloadOrder(orderId);
                order.setStatus(OrderStatus.CANCELLED);
                order.setFailureReason("reconciled:RELEASE");
                order.setUpdatedAt(Instant.now());
                order = orderRepository.saveAndFlush(order);
                saga = reloadSaga(orderId);
                saga.setStatus("RECONCILED");
                saga.setCurrentStep("RELEASED");
                saga.setRetryCount(saga.getRetryCount() + 1);
                saga.setLastError(null);
                saga.setUpdatedAt(Instant.now());
                sagaRepository.saveAndFlush(saga);
            } else if ("CONFIRM".equals(act)) {
                inventoryClient.confirm(order.getReservationId());
                order = reloadOrder(orderId);
                order.setStatus(OrderStatus.CONFIRMED);
                order.setFailureReason(null);
                order.setUpdatedAt(Instant.now());
                order = orderRepository.saveAndFlush(order);
                saga = reloadSaga(orderId);
                saga.setStatus("COMPLETED");
                saga.setCurrentStep("CONFIRMED");
                saga.setRetryCount(saga.getRetryCount() + 1);
                saga.setLastError(null);
                saga.setUpdatedAt(Instant.now());
                sagaRepository.saveAndFlush(saga);
            } else {
                throw new SalesException("INVALID_ACTION", "action must be RELEASE or CONFIRM", 400);
            }
        } catch (SalesException ex) {
            throw ex;
        } catch (Exception ex) {
            saga = reloadSaga(orderId);
            saga.setLastError("reconcile " + act + " failed: " + ex.getMessage());
            saga.setRetryCount(saga.getRetryCount() + 1);
            saga.setUpdatedAt(Instant.now());
            sagaRepository.saveAndFlush(saga);
            throw new SalesException("RECONCILE_FAILED", ex.getMessage() == null ? "reconcile failed" : ex.getMessage(), 502);
        }
        return get(orderId);
    }

    private SalesOrderResponse toResponse(SalesOrderEntity o, OrderSagaEntity saga) {
        return new SalesOrderResponse(
                o.getId(), o.getOrderNumber(), o.getCustomerId(), o.getStatus().name(),
                o.getInventoryItemId(), o.getQuantity(), o.getReservationId(), o.getCorrelationId(),
                o.getFailureReason(),
                saga == null ? null : saga.getStatus(),
                saga == null ? null : saga.getCurrentStep(),
                o.getCreatedAt()
        );
    }
}
