package com.agricore.inventory.infrastructure.persistence.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "inventory_items")
public class InventoryItemEntity {
    @Id
    private UUID id;
    @Column(name = "warehouse_id", nullable = false)
    private UUID warehouseId;
    @Column(nullable = false, length = 64)
    private String sku;
    @Column(nullable = false, length = 200)
    private String name;
    @Column(name = "item_type", nullable = false, length = 32)
    private String itemType;
    @Column(nullable = false, length = 16)
    private String unit;
    @Column(name = "on_hand_quantity", nullable = false, precision = 18, scale = 3)
    private BigDecimal onHandQuantity;
    @Column(name = "reserved_quantity", nullable = false, precision = 18, scale = 3)
    private BigDecimal reservedQuantity;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Version
    private long version;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getWarehouseId() { return warehouseId; }
    public void setWarehouseId(UUID warehouseId) { this.warehouseId = warehouseId; }
    public String getSku() { return sku; }
    public void setSku(String sku) { this.sku = sku; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getItemType() { return itemType; }
    public void setItemType(String itemType) { this.itemType = itemType; }
    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }
    public BigDecimal getOnHandQuantity() { return onHandQuantity; }
    public void setOnHandQuantity(BigDecimal onHandQuantity) { this.onHandQuantity = onHandQuantity; }
    public BigDecimal getReservedQuantity() { return reservedQuantity; }
    public void setReservedQuantity(BigDecimal reservedQuantity) { this.reservedQuantity = reservedQuantity; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public long getVersion() { return version; }

    public BigDecimal availableQuantity() {
        return onHandQuantity.subtract(reservedQuantity);
    }
}
