package com.agricore.inventory.application.service;

import com.agricore.inventory.api.response.InternalWarehouseScopeResponse;
import com.agricore.inventory.domain.exception.InventoryException;
import com.agricore.inventory.infrastructure.persistence.WarehouseJpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class InventoryWarehouseScopeService {

    private final WarehouseJpaRepository warehouseRepository;

    public InventoryWarehouseScopeService(WarehouseJpaRepository warehouseRepository) {
        this.warehouseRepository = warehouseRepository;
    }

    @Transactional(readOnly = true)
    public InternalWarehouseScopeResponse getScope(UUID warehouseId) {
        var warehouse = warehouseRepository.findById(warehouseId)
                .orElseThrow(() -> new InventoryException(
                        "WAREHOUSE_NOT_FOUND",
                        "Warehouse not found",
                        404
                ));
        if (warehouse.getFarmId() == null) {
            throw new InventoryException(
                    "WAREHOUSE_SCOPE_UNAVAILABLE",
                    "Warehouse farm scope is unavailable",
                    503
            );
        }
        return new InternalWarehouseScopeResponse(warehouse.getId(), warehouse.getFarmId());
    }
}
