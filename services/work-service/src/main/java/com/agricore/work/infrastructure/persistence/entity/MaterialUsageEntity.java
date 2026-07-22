package com.agricore.work.infrastructure.persistence.entity;

import com.agricore.work.domain.model.MaterialUsageStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "material_usages")
public class MaterialUsageEntity {

    @Id
    private UUID id;

    @Column(name = "work_task_id", nullable = false)
    private UUID workTaskId;

    @Column(name = "inventory_item_id", nullable = false)
    private UUID inventoryItemId;

    @Column(nullable = false, precision = 18, scale = 3)
    private BigDecimal quantity;

    @Column(length = 16)
    private String unit;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private MaterialUsageStatus status;

    @Column(name = "inventory_reference_id", nullable = false, length = 100)
    private String inventoryReferenceId;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getWorkTaskId() { return workTaskId; }
    public void setWorkTaskId(UUID workTaskId) { this.workTaskId = workTaskId; }
    public UUID getInventoryItemId() { return inventoryItemId; }
    public void setInventoryItemId(UUID inventoryItemId) { this.inventoryItemId = inventoryItemId; }
    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }
    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }
    public MaterialUsageStatus getStatus() { return status; }
    public void setStatus(MaterialUsageStatus status) { this.status = status; }
    public String getInventoryReferenceId() { return inventoryReferenceId; }
    public void setInventoryReferenceId(String inventoryReferenceId) { this.inventoryReferenceId = inventoryReferenceId; }
    public String getLastError() { return lastError; }
    public void setLastError(String lastError) { this.lastError = lastError; }
    public Instant getConsumedAt() { return consumedAt; }
    public void setConsumedAt(Instant consumedAt) { this.consumedAt = consumedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public long getVersion() { return version; }
}
