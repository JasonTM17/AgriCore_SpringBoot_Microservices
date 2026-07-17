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

        try {
            UUID reservationId = inventoryClient.reserve(
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
            saga.setLastError(ex.getMessage());
            saga.setCurrentStep("COMPENSATED");
            saga.setUpdatedAt(Instant.now());
            saga = sagaRepository.saveAndFlush(saga);
        } catch (Exception ex) {
            // Compensation: release if we already reserved
            order = reloadOrder(order.getId());
            if (order.getReservationId() != null) {
                try {
                    inventoryClient.release(order.getReservationId());
                } catch (Exception releaseEx) {
                    saga = reloadSaga(order.getId());
                    saga.setLastError("Release failed: " + releaseEx.getMessage());
                    sagaRepository.saveAndFlush(saga);
                }
            }
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
