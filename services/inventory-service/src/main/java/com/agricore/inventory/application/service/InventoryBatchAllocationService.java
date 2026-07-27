package com.agricore.inventory.application.service;

import com.agricore.inventory.domain.exception.InventoryException;
import com.agricore.inventory.infrastructure.persistence.InventoryBatchJpaRepository;
import com.agricore.inventory.infrastructure.persistence.InventoryReservationAllocationJpaRepository;
import com.agricore.inventory.infrastructure.persistence.entity.InventoryBatchEntity;
import com.agricore.inventory.infrastructure.persistence.entity.InventoryReservationAllocationEntity;
import com.agricore.inventory.infrastructure.persistence.entity.InventoryItemEntity;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

@Component
public class InventoryBatchAllocationService {

    private final InventoryBatchJpaRepository batchRepository;
    private final InventoryReservationAllocationJpaRepository allocationRepository;

    public InventoryBatchAllocationService(
            InventoryBatchJpaRepository batchRepository,
            InventoryReservationAllocationJpaRepository allocationRepository
    ) {
        this.batchRepository = batchRepository;
        this.allocationRepository = allocationRepository;
    }

    @Transactional
    public void ensureOpeningBatch(InventoryItemEntity item) {
        String lotCode = "OPENING-" + item.getId();
        if (batchRepository.findByInventoryItemIdAndLotCode(item.getId(), lotCode).isPresent()) {
            return;
        }
        Instant now = item.getCreatedAt() == null ? Instant.now() : item.getCreatedAt();
        batchRepository.save(newBatch(
                item.getId(),
                lotCode,
                now,
                null,
                item.getOnHandQuantity(),
                item.getReservedQuantity(),
                now
        ));
    }

    @Transactional
    public InventoryBatchEntity addStock(
            UUID itemId,
            BigDecimal quantity,
            String lotCode,
            Instant expiresAt,
            Instant receivedAt
    ) {
        Instant now = Instant.now();
        InventoryBatchEntity batch = batchRepository
                .findByInventoryItemIdAndLotCode(itemId, lotCode)
                .orElseGet(() -> newBatch(itemId, lotCode, receivedAt, expiresAt,
                        BigDecimal.ZERO, BigDecimal.ZERO, now));
        if (batch.getExpiresAt() != null && !Objects.equals(batch.getExpiresAt(), expiresAt)) {
            throw new InventoryException(
                    "BATCH_LOT_CONFLICT",
                    "Lot code already exists with a different expiry",
                    409
            );
        }
        if (batch.getExpiresAt() == null && expiresAt != null && batch.getQuantity().signum() > 0) {
            throw new InventoryException(
                    "BATCH_LOT_CONFLICT",
                    "Lot code already exists without an expiry",
                    409
            );
        }
        batch.setQuantity(batch.getQuantity().add(quantity));
        batch.setUpdatedAt(now);
        return batchRepository.save(batch);
    }

    @Transactional
    public List<BatchAllocation> deduct(UUID itemId, BigDecimal quantity, Instant now) {
        List<InventoryBatchEntity> batches = lockedBatches(itemId);
        List<BatchAllocation> allocations = allocate(batches, quantity, now);
        for (BatchAllocation allocation : allocations) {
            InventoryBatchEntity batch = batchById(batches, allocation.batchId());
            batch.setQuantity(batch.getQuantity().subtract(allocation.quantity()));
            batch.setUpdatedAt(now);
        }
        batchRepository.saveAll(batches);
        return allocations;
    }

    @Transactional
    public List<BatchAllocation> reserve(
            UUID reservationId,
            UUID itemId,
            BigDecimal quantity,
            Instant now
    ) {
        List<InventoryBatchEntity> batches = lockedBatches(itemId);
        List<BatchAllocation> allocations = allocate(batches, quantity, now);
        for (BatchAllocation allocation : allocations) {
            InventoryBatchEntity batch = batchById(batches, allocation.batchId());
            batch.setReservedQuantity(batch.getReservedQuantity().add(allocation.quantity()));
            batch.setUpdatedAt(now);
        }
        batchRepository.saveAll(batches);
        List<InventoryReservationAllocationEntity> allocationEntities =
                new ArrayList<>(allocations.size());
        for (int index = 0; index < allocations.size(); index++) {
            allocationEntities.add(newAllocation(
                    reservationId,
                    allocations.get(index),
                    now.plusMillis(index)
            ));
        }
        allocationRepository.saveAll(allocationEntities);
        return allocations;
    }

    @Transactional
    public void release(UUID reservationId, UUID itemId, Instant now) {
        List<InventoryBatchEntity> batches = lockedBatches(itemId);
        List<InventoryReservationAllocationEntity> allocations = allocations(reservationId);
        for (InventoryReservationAllocationEntity allocation : allocations) {
            InventoryBatchEntity batch = batchById(batches, allocation.getBatchId());
            BigDecimal nextReserved = batch.getReservedQuantity().subtract(allocation.getQuantity());
            if (nextReserved.signum() < 0) {
                throw allocationStateInvalid();
            }
            batch.setReservedQuantity(nextReserved);
            batch.setUpdatedAt(now);
        }
        batchRepository.saveAll(batches);
    }

    @Transactional
    public List<BatchAllocation> fulfill(UUID reservationId, UUID itemId, Instant now) {
        List<InventoryBatchEntity> batches = lockedBatches(itemId);
        List<InventoryReservationAllocationEntity> allocations = allocations(reservationId);
        for (InventoryReservationAllocationEntity allocation : allocations) {
            InventoryBatchEntity batch = batchById(batches, allocation.getBatchId());
            if (batch.getQuantity().compareTo(allocation.getQuantity()) < 0
                    || batch.getReservedQuantity().compareTo(allocation.getQuantity()) < 0) {
                throw new InventoryException(
                        "INSUFFICIENT_ON_HAND",
                        "Batch quantity is below the reserved allocation",
                        409
                );
            }
            batch.setQuantity(batch.getQuantity().subtract(allocation.getQuantity()));
            batch.setReservedQuantity(batch.getReservedQuantity().subtract(allocation.getQuantity()));
            batch.setUpdatedAt(now);
        }
        batchRepository.saveAll(batches);
        return allocations.stream()
                .map(allocation -> new BatchAllocation(allocation.getBatchId(), allocation.getQuantity()))
                .toList();
    }

    private List<InventoryBatchEntity> lockedBatches(UUID itemId) {
        List<InventoryBatchEntity> batches = batchRepository.findAllByInventoryItemIdForUpdate(itemId);
        if (batches.isEmpty()) {
            throw new InventoryException(
                    "BATCH_STATE_UNAVAILABLE",
                    "Inventory item has no stock batch state",
                    500
            );
        }
        return batches;
    }

    private List<BatchAllocation> allocate(
            List<InventoryBatchEntity> batches,
            BigDecimal requested,
            Instant now
    ) {
        BigDecimal allAvailable = BigDecimal.ZERO;
        BigDecimal eligibleAvailable = BigDecimal.ZERO;
        for (InventoryBatchEntity batch : batches) {
            BigDecimal available = batch.availableQuantity();
            allAvailable = allAvailable.add(available);
            if (!batch.isExpiredAt(now)) {
                eligibleAvailable = eligibleAvailable.add(available);
            }
        }
        if (allAvailable.compareTo(requested) < 0) {
            throw new InventoryException("INSUFFICIENT_STOCK", "Not enough available stock", 409);
        }
        if (eligibleAvailable.compareTo(requested) < 0) {
            throw new InventoryException("EXPIRED_STOCK", "Available stock is expired", 409);
        }

        BigDecimal remaining = requested;
        List<BatchAllocation> allocations = new ArrayList<>();
        for (InventoryBatchEntity batch : batches) {
            if (batch.isExpiredAt(now) || batch.availableQuantity().signum() <= 0) {
                continue;
            }
            BigDecimal allocated = remaining.min(batch.availableQuantity());
            allocations.add(new BatchAllocation(batch.getId(), allocated));
            remaining = remaining.subtract(allocated);
            if (remaining.signum() == 0) {
                return allocations;
            }
        }
        throw allocationStateInvalid();
    }

    private List<InventoryReservationAllocationEntity> allocations(UUID reservationId) {
        List<InventoryReservationAllocationEntity> allocations =
                allocationRepository.findAllByReservationIdOrderByCreatedAtAscIdAsc(reservationId);
        if (allocations.isEmpty()) {
            throw new InventoryException(
                    "RESERVATION_ALLOCATION_UNAVAILABLE",
                    "Reservation has no batch allocations",
                    500
            );
        }
        return allocations;
    }

    private static InventoryBatchEntity batchById(List<InventoryBatchEntity> batches, UUID batchId) {
        return batches.stream()
                .filter(batch -> batch.getId().equals(batchId))
                .findFirst()
                .orElseThrow(InventoryBatchAllocationService::allocationStateInvalid);
    }

    private static InventoryBatchEntity newBatch(
            UUID itemId,
            String lotCode,
            Instant receivedAt,
            Instant expiresAt,
            BigDecimal quantity,
            BigDecimal reservedQuantity,
            Instant now
    ) {
        InventoryBatchEntity batch = new InventoryBatchEntity();
        batch.setId(UUID.randomUUID());
        batch.setInventoryItemId(itemId);
        batch.setLotCode(lotCode);
        batch.setReceivedAt(receivedAt);
        batch.setExpiresAt(expiresAt);
        batch.setQuantity(quantity);
        batch.setReservedQuantity(reservedQuantity);
        batch.setCreatedAt(now);
        batch.setUpdatedAt(now);
        return batch;
    }

    private static InventoryReservationAllocationEntity newAllocation(
            UUID reservationId,
            BatchAllocation allocation,
            Instant createdAt
    ) {
        InventoryReservationAllocationEntity entity = new InventoryReservationAllocationEntity();
        entity.setId(UUID.randomUUID());
        entity.setReservationId(reservationId);
        entity.setBatchId(allocation.batchId());
        entity.setQuantity(allocation.quantity());
        entity.setCreatedAt(createdAt);
        return entity;
    }

    private static InventoryException allocationStateInvalid() {
        return new InventoryException(
                "BATCH_STATE_INVALID",
                "Inventory batch state is inconsistent",
                500
        );
    }

    public record BatchAllocation(UUID batchId, BigDecimal quantity) {
    }
}
