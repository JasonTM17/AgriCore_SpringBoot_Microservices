package com.agricore.inventory.application.service;

import com.agricore.inventory.api.request.*;
import com.agricore.inventory.api.response.InventoryItemResponse;
import com.agricore.inventory.api.response.ReservationResponse;
import com.agricore.inventory.api.response.WarehouseResponse;
import com.agricore.inventory.domain.exception.InventoryException;
import com.agricore.inventory.domain.model.MovementType;
import com.agricore.inventory.infrastructure.persistence.*;
import com.agricore.inventory.infrastructure.persistence.entity.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Service
public class InventoryApplicationService {

    public static final String HARVEST_CONSUMER = "inventory-harvest-completed";

    private final WarehouseJpaRepository warehouseRepository;
    private final InventoryItemJpaRepository itemRepository;
    private final StockMovementJpaRepository movementRepository;
    private final ProcessedEventJpaRepository processedEventRepository;
    private final InventoryReservationJpaRepository reservationRepository;
    private final InventoryMetrics metrics;
    private final InventoryEventOutboxWriter eventWriter;

    public InventoryApplicationService(
            WarehouseJpaRepository warehouseRepository,
            InventoryItemJpaRepository itemRepository,
            StockMovementJpaRepository movementRepository,
            ProcessedEventJpaRepository processedEventRepository,
            InventoryReservationJpaRepository reservationRepository,
            InventoryMetrics metrics,
            InventoryEventOutboxWriter eventWriter
    ) {
        this.warehouseRepository = warehouseRepository;
        this.itemRepository = itemRepository;
        this.movementRepository = movementRepository;
        this.processedEventRepository = processedEventRepository;
        this.reservationRepository = reservationRepository;
        this.metrics = metrics;
        this.eventWriter = eventWriter;
    }

    @Transactional
    public WarehouseResponse createWarehouse(CreateWarehouseRequest request) {
        String code = request.code().trim().toUpperCase();
        if (warehouseRepository.existsByCodeIgnoreCase(code)) {
            throw new InventoryException("WAREHOUSE_EXISTS", "Warehouse code already exists", 409);
        }
        WarehouseEntity wh = new WarehouseEntity();
        wh.setId(UUID.randomUUID());
        wh.setCode(code);
        wh.setName(request.name().trim());
        wh.setCreatedAt(Instant.now());
        warehouseRepository.save(wh);
        return new WarehouseResponse(wh.getId(), wh.getCode(), wh.getName(), wh.getCreatedAt());
    }

    @Transactional
    public InventoryItemResponse createItem(CreateItemRequest request) {
        if (!warehouseRepository.existsById(request.warehouseId())) {
            throw new InventoryException("WAREHOUSE_NOT_FOUND", "Warehouse not found", 404);
        }
        String sku = request.sku().trim().toUpperCase();
        if (itemRepository.findByWarehouseIdAndSkuIgnoreCase(request.warehouseId(), sku).isPresent()) {
            throw new InventoryException("SKU_EXISTS", "SKU already exists in warehouse", 409);
        }
        Instant now = Instant.now();
        InventoryItemEntity item = new InventoryItemEntity();
        item.setId(UUID.randomUUID());
        item.setWarehouseId(request.warehouseId());
        item.setSku(sku);
        item.setName(request.name().trim());
        item.setItemType(request.itemType().trim().toUpperCase());
        item.setUnit(request.unit().trim().toUpperCase());
        item.setOnHandQuantity(BigDecimal.ZERO);
        item.setReservedQuantity(BigDecimal.ZERO);
        item.setCreatedAt(now);
        item.setUpdatedAt(now);
        itemRepository.save(item);
        return toItemResponse(item);
    }

    @Transactional
    public InventoryItemResponse stockIn(StockInRequest request) {
        InventoryItemEntity item = requireItem(request.inventoryItemId());
        if (request.quantity().signum() <= 0) {
            throw new InventoryException("INVALID_QTY", "Quantity must be positive", 400);
        }
        item.setOnHandQuantity(item.getOnHandQuantity().add(request.quantity()));
        item.setUpdatedAt(Instant.now());
        itemRepository.save(item);
        StockMovementEntity movement = writeMovement(item.getId(), MovementType.STOCK_IN, request.quantity(),
                request.referenceType(), request.referenceId(), request.note());
        eventWriter.stockAdded(item, movement);
        return toItemResponse(item);
    }

    @Transactional
    public InventoryItemResponse stockOut(StockOutRequest request) {
        InventoryItemEntity item = requireItemForUpdate(request.inventoryItemId());
        String referenceType = request.referenceType().trim();
        String referenceId = request.referenceId().trim();
        var existingMovement = movementRepository.findFirstByInventoryItemIdAndMovementTypeAndReferenceTypeAndReferenceId(
                item.getId(), MovementType.STOCK_OUT, referenceType, referenceId
        );
        if (existingMovement.isPresent()) {
            if (existingMovement.get().getQuantity().compareTo(request.quantity()) != 0) {
                throw new InventoryException(
                        "IDEMPOTENCY_KEY_REUSED",
                        "Stock-out reference was already used with a different quantity",
                        409
                );
            }
            return toItemResponse(item);
        }
        if (request.quantity().signum() <= 0) {
            throw new InventoryException("INVALID_QTY", "Quantity must be positive", 400);
        }
        if (item.availableQuantity().compareTo(request.quantity()) < 0) {
            throw new InventoryException("INSUFFICIENT_STOCK", "Not enough available stock", 409);
        }

        item.setOnHandQuantity(item.getOnHandQuantity().subtract(request.quantity()));
        item.setUpdatedAt(Instant.now());
        itemRepository.save(item);
        StockMovementEntity movement = writeMovement(
                item.getId(), MovementType.STOCK_OUT, request.quantity(),
                referenceType, referenceId, request.note()
        );
        eventWriter.stockDeducted(item, movement, null);
        return toItemResponse(item);
    }

    /**
     * Idempotent handler for HarvestCompleted.v1.
     * Same eventId processed twice must not double stock.
     */
    @Transactional
    public InventoryItemResponse processHarvestCompleted(HarvestCompletedCommand command) {
        if (processedEventRepository.existsByEventIdAndConsumerName(command.eventId(), HARVEST_CONSUMER)) {
            metrics.recordDuplicateHarvestEvent();
            InventoryItemEntity existing = itemRepository
                    .findByWarehouseIdAndSkuIgnoreCase(command.warehouseId(), command.productCode())
                    .orElseThrow(() -> new InventoryException("ITEM_NOT_FOUND",
                            "Item missing after prior event processing", 500));
            return toItemResponse(existing);
        }

        if (!warehouseRepository.existsById(command.warehouseId())) {
            throw new InventoryException("WAREHOUSE_NOT_FOUND", "Warehouse not found", 404);
        }

        String sku = command.productCode().trim().toUpperCase();
        InventoryItemEntity item = itemRepository
                .findByWarehouseIdAndSkuIgnoreCase(command.warehouseId(), sku)
                .orElseGet(() -> createProduceItem(command.warehouseId(), sku));

        item.setOnHandQuantity(item.getOnHandQuantity().add(command.netWeightKg()));
        item.setUpdatedAt(Instant.now());
        itemRepository.save(item);

        StockMovementEntity movement = writeMovement(
                item.getId(),
                MovementType.STOCK_IN,
                command.netWeightKg(),
                "HarvestBatch",
                command.harvestBatchId().toString(),
                "HarvestCompleted " + command.eventId()
        );
        eventWriter.stockAdded(item, movement);

        processedEventRepository.save(ProcessedEventEntity.of(command.eventId(), HARVEST_CONSUMER));
        metrics.recordAppliedHarvestEvent();
        return toItemResponse(item);
    }

    @Transactional
    public ReservationResponse reserve(ReserveStockRequest request) {
        InventoryItemEntity item = requireItem(request.inventoryItemId());
        if (item.availableQuantity().compareTo(request.quantity()) < 0) {
            metrics.recordReservationFailure();
            eventWriter.inventoryReservationFailed(item, request);
            throw new InventoryException("INSUFFICIENT_STOCK", "Not enough available stock", 409);
        }
        item.setReservedQuantity(item.getReservedQuantity().add(request.quantity()));
        item.setUpdatedAt(Instant.now());
        itemRepository.save(item);

        Instant now = Instant.now();
        InventoryReservationEntity reservation = new InventoryReservationEntity();
        reservation.setId(UUID.randomUUID());
        reservation.setInventoryItemId(item.getId());
        reservation.setQuantity(request.quantity());
        reservation.setStatus("ACTIVE");
        reservation.setReferenceType(request.referenceType().trim());
        reservation.setReferenceId(request.referenceId().trim());
        reservation.setCreatedAt(now);
        reservation.setUpdatedAt(now);
        reservationRepository.save(reservation);

        writeMovement(item.getId(), MovementType.RESERVE, request.quantity(),
                reservation.getReferenceType(), reservation.getReferenceId(), "Reserve");
        eventWriter.inventoryReserved(item, reservation);
        metrics.recordReservationSuccess();
        return new ReservationResponse(
                reservation.getId(), item.getId(), reservation.getQuantity(),
                reservation.getStatus(), reservation.getReferenceType(), reservation.getReferenceId()
        );
    }

    @Transactional
    public ReservationResponse release(UUID reservationId) {
        InventoryReservationEntity reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new InventoryException("RESERVATION_NOT_FOUND", "Reservation not found", 404));
        if ("RELEASED".equals(reservation.getStatus()) || "FULFILLED".equals(reservation.getStatus())) {
            return new ReservationResponse(
                    reservation.getId(), reservation.getInventoryItemId(), reservation.getQuantity(),
                    reservation.getStatus(), reservation.getReferenceType(), reservation.getReferenceId()
            );
        }
        InventoryItemEntity item = requireItem(reservation.getInventoryItemId());
        item.setReservedQuantity(item.getReservedQuantity().subtract(reservation.getQuantity()));
        if (item.getReservedQuantity().signum() < 0) {
            throw new InventoryException("NEGATIVE_RESERVED", "Reserved quantity would go negative", 500);
        }
        item.setUpdatedAt(Instant.now());
        itemRepository.save(item);
        reservation.setStatus("RELEASED");
        reservation.setUpdatedAt(Instant.now());
        reservationRepository.save(reservation);
        writeMovement(item.getId(), MovementType.RELEASE, reservation.getQuantity(),
                reservation.getReferenceType(), reservation.getReferenceId(), "Release");
        eventWriter.inventoryReleased(item, reservation);
        return new ReservationResponse(
                reservation.getId(), item.getId(), reservation.getQuantity(),
                reservation.getStatus(), reservation.getReferenceType(), reservation.getReferenceId()
        );
    }

    /**
     * Commit a reservation: decrement on-hand and reserved quantities (sales saga confirm step).
     * Idempotent when reservation is already FULFILLED.
     */
    @Transactional
    public ReservationResponse confirm(UUID reservationId) {
        InventoryReservationEntity reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new InventoryException("RESERVATION_NOT_FOUND", "Reservation not found", 404));
        if ("FULFILLED".equals(reservation.getStatus())) {
            return new ReservationResponse(
                    reservation.getId(), reservation.getInventoryItemId(), reservation.getQuantity(),
                    reservation.getStatus(), reservation.getReferenceType(), reservation.getReferenceId()
            );
        }
        if (!"ACTIVE".equals(reservation.getStatus())) {
            throw new InventoryException(
                    "RESERVATION_NOT_ACTIVE",
                    "Reservation status is " + reservation.getStatus() + "; only ACTIVE can be confirmed",
                    409
            );
        }
        InventoryItemEntity item = requireItem(reservation.getInventoryItemId());
        if (item.getOnHandQuantity().compareTo(reservation.getQuantity()) < 0) {
            throw new InventoryException("INSUFFICIENT_ON_HAND", "On-hand stock below reserved quantity", 409);
        }
        if (item.getReservedQuantity().compareTo(reservation.getQuantity()) < 0) {
            throw new InventoryException("NEGATIVE_RESERVED", "Reserved quantity below reservation", 500);
        }
        item.setOnHandQuantity(item.getOnHandQuantity().subtract(reservation.getQuantity()));
        item.setReservedQuantity(item.getReservedQuantity().subtract(reservation.getQuantity()));
        item.setUpdatedAt(Instant.now());
        itemRepository.save(item);
        reservation.setStatus("FULFILLED");
        reservation.setUpdatedAt(Instant.now());
        reservationRepository.save(reservation);
        writeMovement(item.getId(), MovementType.CONFIRM, reservation.getQuantity(),
                reservation.getReferenceType(), reservation.getReferenceId(), "Confirm reservation");
        StockMovementEntity stockOutMovement = writeMovement(item.getId(), MovementType.STOCK_OUT, reservation.getQuantity(),
                reservation.getReferenceType(), reservation.getReferenceId(), "Sales fulfillment");
        eventWriter.stockDeducted(item, stockOutMovement, reservation.getId());
        return new ReservationResponse(
                reservation.getId(), item.getId(), reservation.getQuantity(),
                reservation.getStatus(), reservation.getReferenceType(), reservation.getReferenceId()
        );
    }

    @Transactional(readOnly = true)
    public InventoryItemResponse getItem(UUID id) {
        return toItemResponse(requireItem(id));
    }

    private InventoryItemEntity createProduceItem(UUID warehouseId, String sku) {
        Instant now = Instant.now();
        InventoryItemEntity item = new InventoryItemEntity();
        item.setId(UUID.randomUUID());
        item.setWarehouseId(warehouseId);
        item.setSku(sku);
        item.setName(sku);
        item.setItemType("PRODUCE");
        item.setUnit("KG");
        item.setOnHandQuantity(BigDecimal.ZERO);
        item.setReservedQuantity(BigDecimal.ZERO);
        item.setCreatedAt(now);
        item.setUpdatedAt(now);
        return itemRepository.save(item);
    }

    private InventoryItemEntity requireItem(UUID id) {
        return itemRepository.findById(id)
                .orElseThrow(() -> new InventoryException("ITEM_NOT_FOUND", "Inventory item not found", 404));
    }

    private InventoryItemEntity requireItemForUpdate(UUID id) {
        return itemRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new InventoryException("ITEM_NOT_FOUND", "Inventory item not found", 404));
    }

    private StockMovementEntity writeMovement(
            UUID itemId,
            MovementType type,
            BigDecimal qty,
            String refType,
            String refId,
            String note
    ) {
        StockMovementEntity m = new StockMovementEntity();
        m.setId(UUID.randomUUID());
        m.setInventoryItemId(itemId);
        m.setMovementType(type);
        m.setQuantity(qty);
        m.setReferenceType(refType);
        m.setReferenceId(refId);
        m.setNote(note);
        m.setCreatedAt(Instant.now());
        return movementRepository.save(m);
    }

    private InventoryItemResponse toItemResponse(InventoryItemEntity item) {
        return new InventoryItemResponse(
                item.getId(),
                item.getWarehouseId(),
                item.getSku(),
                item.getName(),
                item.getItemType(),
                item.getUnit(),
                item.getOnHandQuantity(),
                item.getReservedQuantity(),
                item.availableQuantity(),
                item.getVersion()
        );
    }
}
